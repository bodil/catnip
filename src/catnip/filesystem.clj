;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.filesystem
  (:require [clojure.java.io :as io]
            [catnip.cljs :as cljs])
  (:use [clojure.test]
        [clojure.set :only [union]]
        [catnip.paths])
  (:import [java.io File]))

(def project-path (.getCanonicalFile (io/file ".")))

(with-test
  (defn parse-gitignore [f]
    (if (.isFile f)
      (set (clojure.string/split-lines (slurp f)))
      #{}))
  (is (= #{"foo" "bar/*"} (parse-gitignore (tempfile "foo\r\nbar/*\r\n")))))

(with-test
  (defn ignored-paths
    "Generate a matcher function for paths to be ignored."
    ([]
       (ignored-paths (parse-gitignore
                       (io/file project-path ".gitignore"))))
    ([gitignore]
       (let [r
             (map glob-matcher
                  (union
                   (set ["/target" "/checkouts"])
                   gitignore))]
         (fn [f]
           (or (not (inside? project-path f))
               (dotfile? f)
               (some #(% (io/file "/" (relative-to project-path f))) r))))))
  (is ((ignored-paths #{"/target"}) (io/file "target")))
  (is ((ignored-paths #{"/target"}) (io/file "target/foo")))
  (is (not ((ignored-paths #{"/target"}) (io/file "flerb")))))

(defn list-dir [path]
  (let [ignore (ignored-paths)]
    (map (partial relative-to project-path)
         (filter #(and (.isFile %) (not (ignore %)))
                 (file-seq path)))))

(defn subpaths [path]
  (let [ignore (ignored-paths)]
    (map (partial relative-to project-path)
         (filter #(and (.isDirectory %) (not= project-path %)
                       (not (ignore %)))
                 (file-seq path)))))

(defn ensure-parent [path]
  (let [parent (.getParentFile path)]
    (when-not (.isDirectory parent)
      (.mkdirs parent))))

(defn save-file [path content]
  (try
    (let [fullpath (io/file project-path path)]
      (ensure-parent fullpath)
      (spit fullpath content))
    {:path path :success true}
    (catch Throwable e
      {:path path :success false :error (.getMessage e)})))

(defn- append-clj-prefix [file]
  (let [parent (.getParentFile file)
        base (.getName file)]
    (io/file parent (str base ".clj"))))

(defn ns-as-local-file [ns]
  (when ns
    (let [ns-path (clojure.string/split ns #"\.")
        ns-file (join-paths project-path "src" ns-path)
        file (append-clj-prefix ns-file)]
    (when (.isFile file)
      (relative-to project-path file)))))

(defn fs-command [msg]
  (let [result (case (:command msg)
                 "files"
                 {:files (list-dir project-path)}
                 "dirs"
                 {:dirs (subpaths project-path)}
                 "read"
                 {:path (:path msg)
                  :file (slurp (io/file project-path (:path msg)))}
                 "save"
                 (save-file (:path msg) (:file msg))
                 "cljsc"
                 (cljs/cljs-compile project-path (:path msg))
                 {:error "Unrecognised command."})]
    (assoc result :command (:command msg))))
