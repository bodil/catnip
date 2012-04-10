(ns leiningen.edit
  (:require [mizugorou.server :as server]
            [clojure.java.browse :as browse])
  (:import [java.util.concurrent CountDownLatch]))

(defn edit
  "Launch a Mizugorou server."
  [project & args]
  (let [[port-str] args
        port (if port-str (Integer. port-str) 1337)
        url (str (server/start port))]
    (println "Mizugorou running on" url)
    (browse/browse-url url))
  (.await (CountDownLatch. 1)))
