(ns leiningen.edit
  (:require [leiningen.core.eval :as eval])
  (:import [java.util.concurrent CountDownLatch]
           [java.net ServerSocket]))

(defn- with-mizugorou-dep [project]
  (assoc project :dependencies
         (conj (:dependencies project)
               '[mizugorou "0.1.0-SNAPSHOT"])))

(defn- start-server-form [port]
  `(let [url# (str (mizugorou.server/start ~port))]
     (println "Mizugorou running on" url#)
     (clojure.java.browse/browse-url url#)))

(defn- server-in-project [project port]
  (eval/eval-in-project project
                        (start-server-form port)
                        `(require 'clojure.java.browse
                                  'mizugorou.server)))

(defn- free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn edit
  "Launch a Mizugorou server."
  [project & args]
  (server-in-project (with-mizugorou-dep project) (free-port))
  (.await (CountDownLatch. 1)))
