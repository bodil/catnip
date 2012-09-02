;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.server
  (:require [cheshire.custom :as json]
            [net.cgrand.enlive-html :as html]
            [catnip.filesystem :as fs]
            [catnip.profile :as profile]
            [catnip.repl :as repl]
            [clojure.repl])
  (:use [clojure.test])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler
            HttpHandler]
           [org.webbitserver.handler EmbeddedResourceHandler]
           [java.net InetSocketAddress URI]
           [java.util.concurrent Executors]))

(json/add-encoder java.lang.Class
                  (fn [c out] (.writeString out (.getName c))))

(json/add-encoder java.lang.Object
                  (fn [c out] (.writeString out (str c))))

(defn send-index [r]
  (let [nodes (html/html-resource "catnip/index.html")
        transformed
        (html/transform nodes [:#session-profile]
                        (html/content (profile/wrap-profile)))]
    (-> r
        (.header "Content-Type" "text/html")
        (.content (apply str (html/emit* transformed)))
        (.end))))

(defn on-connect [socket]
  (.data socket "ns" (create-ns 'user)))

(defn on-disconnect [socket] )

(defn on-message [socket json]
  (let [msg (json/parse-string json true)
        results
        (try
          (cond
            (:eval msg)
            (repl/eval-string socket (:eval msg))

            ;; (:cljs msg)
            ;; (fs/cljs-compile (:cljs msg))

            (:complete msg)
            {:complete (repl/complete-string
                        socket (:complete msg) (:ns msg))}

            (:doc msg)
            {:doc (repl/document-symbol socket (:doc msg) (:ns msg))
             :symbol (:doc msg)}

            (:fs msg)
            {:fs (fs/fs-command (:fs msg))}

            (:profile msg)
            {:profile (profile/save-profile (:profile msg))}

            :else {:error "Bad message" :msg json})
          (catch Exception e
            {:error (repl/pprint-exception e)}))]
    (try
      (.send socket
             (json/generate-string
              (assoc results
                :ns (str (.data socket "ns"))
                :tag (:tag msg))))
      (catch Exception e
        (let [message (repl/pprint-exception e)]
          (.send socket (json/generate-string
                        {:error "Failed to serialise response."
                         :exception message})))))))

(defn start [port]
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
      (.add "/(buffers/.*|$)"
            (proxy [HttpHandler] []
              (handleHttpRequest [req res ctl]
                (send-index res))))
      (.add (EmbeddedResourceHandler. "catnip"))
      (.start))
    (def ^:dynamic *server* server)
    (.getUri server)))

(defn stop []
  (.stop *server*))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "1337"))]
    (start port)))
