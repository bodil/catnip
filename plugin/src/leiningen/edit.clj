(ns leiningen.edit
  (:require [leinjacker.eval :as eval]
            [leinjacker.deps :as deps])
  (:import [java.util.concurrent CountDownLatch]
           [java.net ServerSocket]))

(defn- with-catnip-dep [project]
  (deps/add-if-missing project '[catnip "0.5.1"]))

(defn- start-server-form [port]
  `(let [url# (str (catnip.server/start ~port))]
     (println "Catnip running on" url#)
     (clojure.java.browse/browse-url url#)))

(defn- server-in-project [project port]
  (eval/eval-in-project project
                        (start-server-form port)
                        `(require 'clojure.java.browse
                                  'catnip.server)))

(defn- free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- select-port [port-str]
  (if (nil? port-str) (free-port)
      (let [port (Integer. port-str)]
        (if (pos? port) port
            (free-port)))))

(defn edit
  "Launch a Catnip server."
  [project & [port]]
  (server-in-project (with-catnip-dep project) (select-port port))
  (.await (CountDownLatch. 1)))
