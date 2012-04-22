;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.server
  (:require [clojure.data.json :as json]
            [clojure.repl :as repl]
            [clojure.pprint :as pprint]
            [clojure.contrib.string :as string]
            [catnip.filesystem :as fs]
            [catnip.complete :as complete]
            [catnip.profile :as profile])
  (:use [clojure.test])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler]
           [org.webbitserver.handler EmbeddedResourceHandler AliasHandler]
           [java.net InetSocketAddress URI]
           [java.util.concurrent Executors]))

(defn stream [s]
  (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. s)))

(with-test
  (defn ppr [el]
    (string/trim (with-out-str (pprint/pprint el))))
  (is (= "1337" (ppr 1337))))

(defn eval*
  ([socket sexp]
     (eval* socket sexp {}))
  ([socket sexp bindings]
     (with-bindings
         (assoc bindings #'*ns* (.data socket "ns"))
       (let [result (eval sexp)]
         (.data socket "ns" *ns*)
         result))))

(defmacro with-err-str [& forms]
  `(let [err# (java.io.StringWriter.)]
     (with-bindings {#'*err* err#}
       ~@forms)
     (str err#)))

(defn eval-sexp [socket sexp]
  (let [out (java.io.StringWriter.)
        code-ns (str (.data socket "ns"))]
    (try
      {:code {:ns code-ns :text (ppr sexp)}
       :result (ppr (eval* socket sexp
                           {#'*out* out #'*err* out #'*test-out* out}))
       :out (str out)}
      (catch Exception e
        (let [e (repl/root-cause e)
              msg (.getMessage e)
              chop-exc-re #"^java\.[\w.]+Exception: (.*)$"
              errline-re #":(\d+)\)$"]
          {:code {:ns code-ns :text (ppr sexp)}
           :out (str out)
           :error (with-err-str (repl/pst e))
           :annotation
           (or (if-let [m (re-find chop-exc-re msg)] (second m)) msg)
           :errline
           (if-let [m (re-find errline-re msg)] (Integer. (second m)))})))))

(defn eval-stream [socket s]
  (loop [line (.getLineNumber s)
         sexp (read s false nil)
         results []]
    (if (not (nil? sexp))
      (let [result (assoc (eval-sexp socket sexp) :line line)]
        (if (result :error)
          (conj results result)
          (recur (.getLineNumber s) (read s false nil) (conj results result))))
      results)))

(defn eval-string [socket s]
  (let [s (stream s)]
    (try
      {:eval (eval-stream socket s)}
      (catch Exception e
        (let [e (repl/root-cause e)]
          {:error (with-err-str (repl/pst e))
           :annotation (.getMessage e)
           :line (.getLineNumber s)})))))

(defn resolve-ns [socket ns]
  (if ns (create-ns (symbol ns))
      (if socket (.data socket "ns") (create-ns 'user))))

(defn complete-string [socket s ns]
  (pprint/pprint [s ns])
  (let [ns (resolve-ns socket ns)]
    (complete/completions s ns)))

(defn document-symbol [socket s ns]
  (let [ns (resolve-ns socket ns)]
    (with-out-str
      (with-bindings {#'*ns* ns}
        (let [sym (symbol s)]
          (eval `(clojure.repl/doc ~sym)))))))

(defn on-connect [socket]
  (.data socket "ns" (create-ns 'user)))

(defn on-disconnect [socket] )

(defn on-message [socket json]
  (println (str (json/read-json json)))
  (let [msg (json/read-json json)
        results
        (try
          (cond
            (:eval msg)
            (eval-string socket (:eval msg))

            ;; (:cljs msg)
            ;; (fs/cljs-compile (:cljs msg))

            (:complete msg)
            {:complete (complete-string socket (:complete msg) (:ns msg))}

            (:doc msg)
            {:doc (document-symbol socket (:doc msg) (:ns msg))
             :symbol (:doc msg)}

            (:fs msg)
            {:fs (fs/fs-command (:fs msg))}

            (:profile msg)
            {:profile (profile/read-profile)}
            
            :else {:error "Bad message" :msg json})
          (catch Exception e
            {:error (with-err-str (repl/pst (repl/root-cause e)))}))]
    (try
      (.send socket
             (json/json-str
              (assoc results
                :ns (str (.data socket "ns"))
                :tag (:tag msg))))
      (catch Exception e
        (let [message (with-err-str (repl/pst (repl/root-cause e)))]
          (.send socket (json/json-str
                        {:error "Failed to serialise response."
                         :exception message}))
          (println message))))))

(defn start [port]
  (complete/init)
  (let [server (WebServers/createWebServer
                (Executors/newSingleThreadExecutor)
                (InetSocketAddress. "127.0.0.1" port)
                (URI/create (str "http://localhost:" port)))]
    (doto server
      (.add "/repl"
            (proxy [WebSocketHandler] []
              (onOpen [c] (on-connect c))
              (onClose [c] (on-disconnect c))
              (onMessage [c j] (on-message c j))))
      (.add "/" (AliasHandler. "/index.html"))
      (.add (EmbeddedResourceHandler. "catnip"))
      (.start))
    (.getUri server)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "1337"))]
    (start port)))

