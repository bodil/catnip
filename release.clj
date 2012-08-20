(defn find-version [f]
  (fnext (re-matches #"(?s).*\(defproject catnip \"([^\"]*)\".*" f)))

(defn patch-file [f version]
  (clojure.string/replace f #"((?!lein-)?catnip) \"[^\"]*\""
           (fn [m] (str (m 1) " \"" version "\""))))

(defn sync-file [filename version]
  (spit filename (patch-file (slurp filename) version)))

(let [version (find-version (slurp "project.clj"))
      files (let [initial-set ["project.clj" "plugin/project.clj" "plugin/src/leiningen/edit.clj"]]
              (if (re-matches #".*-SNAPSHOT$" version)
                  initial-set
                (conj initial-set "README.md")))]
  (doseq [f files]
    (sync-file f version)))
