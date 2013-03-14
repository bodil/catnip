;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.server
  (:require [net.cgrand.enlive-html :as html]
            [catnip.filesystem :as fs]
            [catnip.profile :as profile]
            [catnip.repl.util :as repl]
            [catnip.repl.jvm :as jvm]
            [catnip.repl.node :as node]
            [catnip.complete :as complete]
            [catnip.project-clj :as project-clj]
            [catnip.edn :as edn]
            [clojure.edn]
            [cemerick.piggieback :as piggieback]
            [cljs.repl.browser])
  (:use [clojure.test]
        [catnip.webbit :only [relative-file-handler]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler
            HttpHandler]
           [org.webbitserver.handler EmbeddedResourceHandler]
           [java.net InetSocketAddress URI]
           [java.util.concurrent Executors]))

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
  (.data socket "ns" (atom {:jvm (create-ns 'user)
                            :node 'cljs.user})))

(defn on-disconnect [socket]
  (node/cleanup socket))

(defn on-message [socket msg-str]
  (future
    (let [msg (clojure.edn/read-string msg-str)
          target (:target msg)
          results
          (try
            (cond
             (:annotate msg)
             (repl/eval-string socket (:path msg) (:annotate msg)
                               (case target
                                 :clj jvm/annotate-sexp
                                 :node node/annotate-sexp))

             (:eval msg)
             (repl/eval-string socket (:path msg) (:eval msg)
                               (case target
                                 :clj jvm/eval-sexp
                                 :node node/eval-sexp))

             (:complete msg)
             {:complete (jvm/complete-string
                         socket (:complete msg) (:ns msg))}

             (:doc msg)
             {:doc (jvm/document-symbol socket (:doc msg) (:ns msg))
              :symbol (:doc msg)}

             (:fs msg)
             {:fs (fs/fs-command (:fs msg))}

             (:profile msg)
             {:profile (profile/save-profile (:profile msg))}

             :else {:error "Bad message" :msg msg-str})
            (catch Exception e
              {:error (repl/pprint-exception e)}))]
      (try
        (.send socket
               (edn/to-edn
                (assoc results
                  :ns (str (repl/socket-ns socket target))
                  :tag (:tag msg))))
        (catch Exception e
          (let [message (repl/pprint-exception e)]
            (.send socket (str
                           {:error "Failed to serialise response."
                            :exception message}))))))))

(defn start [port]
  (complete/init)
  (let [server (WebServers/createWebServer
                (Executors/newSingleThreadExecutor)
                (InetSocketAddress. "127.0.0.1" port)
                (URI/create (str "http://localhost:" port)))
        project (project-clj/read-project-clj)]
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
      (.add (EmbeddedResourceHandler. "catnip")))
    (doseq [[mount-point local-path] (project-clj/catnip-key :mount project)]
      (.add server (relative-file-handler mount-point local-path)))
    (.start server)
    (def ^:dynamic *server* server)
    (.getUri server)))

(defn stop []
  (.stop *server*))

(defn run-repl []
  (piggieback/cljs-repl
   :repl-env (doto (cljs.repl.browser/repl-env :port 9337)
               cljs.repl/-setup)))

(defn -main [& m]
  (let [port (Integer. (get (System/getenv) "PORT" "1337"))]
    (start port)))
