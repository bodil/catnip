(ns catnip.path
  (:require [clojure.string :as s]))

(defn path-part [path]
  (s/join "/" (butlast (s/split path #"/"))))

(defn file-part [path]
  (last (s/split path #"/")))

(defn file-extension [path]
  (let [parts (s/split (file-part path) #"\.")]
    (if (> (count parts) 1) (last parts) nil)))
