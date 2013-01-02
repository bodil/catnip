(ns catnip.requirejs
  (:use [clojure.string :only [join split]]))

(defn- path-to-string [path]
  (join "/" (split (name path) #"\.")))

(defn- refer-symbols [path syms]
  (let [pkg (gensym "package")]
    `(let [~pkg (js/require ~path)]
       ~@(for [sym syms]
           `(def ~sym (aget ~pkg ~(name sym)))))))

(defn- spec-to-require [spec]
  (let [[path cmd arg] spec
        path (path-to-string path)]
    (case cmd
      :as `(def ~arg (js/require ~path))
      :refer (refer-symbols path arg)
      :only (refer-symbols path arg))))

(defmacro require
  "Takes a set of specs and imports RequireJS modules into the
current namespace accordingly. Specs can take the following forms:

    [path.to.package :as local-name]
    [path.to.package :refer [symbol-1 symbol-2 ...]]

Note that all modules referenced in this way must already be loaded.
Declare them as dependencies to cljs/main in index.html."
  [& specs]
  `(do ~@(map spec-to-require specs)))
