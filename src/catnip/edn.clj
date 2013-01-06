(ns catnip.edn
  (:import [clojure.lang Namespace]))

(declare sanitise-pair)

(defn- sanitise-value [v]
  (cond
   (map? v) (apply hash-map (mapcat sanitise-pair v))
   (vector? v) (apply vector (map sanitise-value v))
   (set? v) (apply hash-set (map sanitise-value v))
   (seq? v) (map sanitise-value v)
   (fn? v) (str v)
   (instance? Namespace v) (ns-name v)
   :else v))

(defn- sanitise-pair [[k v]]
  (map sanitise-value [k v]))

(defn to-edn [data]
  (str (sanitise-value data)))
