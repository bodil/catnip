;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns mizugorou.filesystem
  (:require [clojure.java.io :as io]
            [cljs.closure :as cljsc])
  (:use [clojure.test])
  (:import [java.io File]))

(def project-path (.getCanonicalFile (io/file ".")))
(def ignored-paths (map #(io/file project-path %) ["target" "checkouts" ".git"]))

(with-test
    (defn inside?
      "Tests if a file is inside a given path."
      [^File path ^File file]
      (let [abspath (.getAbsolutePath path)
            absfile (.getAbsolutePath file)]
        (.startsWith absfile abspath)))
  (is (inside? (io/file "/foo/bar") (io/file "/foo/bar/gazonk.clj")))
  (is (not (inside? (io/file "/foo/bar") (io/file "/foo/gazonk.clj")))))

(with-test
    (defn inside-none?
      "Test if a file isn't inside any one of the given paths."
      [paths ^File file]
      (not-any? #(inside? % file) paths))
  (is (not (inside-none? [(io/file "/foo/bar") (io/file "/bar/foo")] (io/file "/bar/foo/quux"))))
  (is (inside-none? [(io/file "/foo/bar") (io/file "/bar/foo")] (io/file "/quux/foo/bar"))))

(with-test
    (defn relative-to
      "Returns the path to file relative to path. File must be inside path."
      [path file]
      {:pre [(inside? path file)]}
      (let [abspath (.getAbsolutePath path)
            absfile (.getAbsolutePath file)]
        (loop [fn (.substring absfile (.length abspath))]
          (if (.startsWith fn File/separator)
            (recur (.substring fn (.length File/separator)))
            fn))))
  (is (= "baz/gazonk.clj" (relative-to (io/file "/foo/bar")
                                       (io/file "/foo/bar/baz/gazonk.clj"))))
  (is (= AssertionError
         (try (relative-to (io/file "/o/hai") (io/file "/foo/bar/gazonk.clj"))
              (catch AssertionError e (.getClass e))))))

(defn dir [path]
  (map (partial relative-to project-path)
       (filter #(and (.isFile %) (inside-none? ignored-paths %))
               (file-seq path))))

(defn subpaths [path]
  (map (partial relative-to project-path)
       (filter #(and (.isDirectory %) (not (= project-path %))
                     (inside-none? ignored-paths %))
               (file-seq path))))

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

(defn fs-command [msg]
  (let [result (case (:command msg)
                 "files"
                 {:files (dir project-path)}
                 "dirs"
                 {:dirs (subpaths project-path)}
                 "read"
                 {:path (:path msg)
                  :file (slurp (io/file project-path (:path msg)))}
                 "save"
                 (save-file (:path msg) (:file msg))
                 {:error "Unrecognised command."})]
      (assoc result :command (:command msg))))

(defn cljs-compile [path]
  (let [fullpath (str (io/file project-path path))
        outpath (str (io/file project-path "resources" "public" "cljs"))
        outfile (str (io/file project-path "resources" "public" "cljs" "bootstrap.js"))]
    (try
      (with-out-str (cljsc/build path
                                 {:output-dir outpath
                                  :output-to outfile
                                  :optimizations :simple
                                  :pretty-print true}))
      {:success true :output ""}
      (catch Throwable e
        {:success false :error (.getMessage e)}))))

