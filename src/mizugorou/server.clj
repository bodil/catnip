;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns mizugorou.server
  (:require [clojure.data.json :as json]
            [clojure.repl :as repl]
            [clojure.pprint :as pprint]
            [clojure.contrib.string :as string]
            [complete.core :as complete])
  (:use clojure.test)
  (:import [org.webbitserver WebServer WebServers WebSocketHandler]
           [org.webbitserver.handler StaticFileHandler]))

(defn stream [s]
  (java.io.PushbackReader. (java.io.StringReader. s)))

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
       :result (ppr (eval* socket sexp {#'*out* out #'*err* out}))
       :out (str out)}
      (catch Exception e
        {:code {:ns code-ns :text (ppr sexp)}
         :error (with-err-str (repl/pst (repl/root-cause e)))
         :out (str out)}))))

(defn eval-stream [socket s]
  (loop [sexp (read s false nil)
         results []]
    (if (not (nil? sexp))
      (let [result (eval-sexp socket sexp)]
        (if (result :error)
          (conj results result)
          (recur (read s false nil) (conj results result))))
      results)))

(defn eval-string [socket s]
  (eval-stream socket (stream s)))

(defn complete-string [socket s ns]
  (complete/completions s (or ns (.data socket "ns"))))

(defn on-connect [socket]
  (.data socket "ns" (create-ns 'user)))

(defn on-disconnect [socket] )

(defn on-message [socket json]
  (let [msg (json/read-json json)
        results
        (cond
          (:eval msg)
          {:eval (eval-string socket (:eval msg))}
          (:complete msg)
          {:complete (complete-string socket (:complete msg) (:ns msg))}
          :else {:error "Bad message"})]
    (.send socket
           (json/json-str
            (assoc results
              :ns (str (.data socket "ns"))
              :tag (:tag msg))))))

(defn start [port]
  (doto (WebServers/createWebServer port)
    (.add "/repl"
          (proxy [WebSocketHandler] []
            (onOpen [c] (on-connect c))
            (onClose [c] (on-disconnect c))
            (onMessage [c j] (on-message c j))))
    (.add (StaticFileHandler. "static"))
    (.start)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "1337"))]
    (println "Listening on port" port)
    (start port)))

