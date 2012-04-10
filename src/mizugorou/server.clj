;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns mizugorou.server
  (:require [clojure.data.json :as json]
            [clojure.repl :as repl]
            [clojure.pprint :as pprint]
            [clojure.contrib.string :as string]
            [mizugorou.filesystem :as fs]
            [mizugorou.complete :as complete])
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

(defn complete-string [socket s ns]
  (complete/completions s (or ns (.data socket "ns"))))

(defn on-connect [socket]
  (.data socket "ns" (create-ns 'user)))

(defn on-disconnect [socket] )

(defn on-message [socket json]
  (let [msg (json/read-json json)
        results
        (try
          (cond
            (:eval msg)
            (eval-string socket (:eval msg))

            (:complete msg)
            {:complete (complete-string socket (:complete msg) (:ns msg))}

            (:fs msg)
            {:fs (fs/fs-command (:fs msg))}

            :else {:error "Bad message"})
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
      (.add (EmbeddedResourceHandler. "mizugorou"))
      (.start))
    (.getUri server)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "1337"))]
    (start port)))

