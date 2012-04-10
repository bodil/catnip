;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns mizugorou.filesystem
  (:require [clojure.java.io :as io])
  (:use [clojure.test])
  (:import [java.io File]))

(def project-path (.getCanonicalFile (File. ".")))
(def target-path (File. project-path "target"))
(def git-path (File. project-path ".git"))

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
  (filter #(and (.isFile %) (not (inside? target-path %))
                (not (inside? git-path %)))
          (file-seq path)))

(defn save-file [path content]
  (try
    (with-open [out (io/writer (File. project-path path))]
      (.write out content))
    {:path path :success true}
    (catch Throwable e
      {:path path :success false :error (.getMessage e)})))

(defn fs-command [msg]
  (let [result (case (:command msg)
                 "files"
                 {:files (map (partial relative-to project-path)
                              (dir project-path))}
                 "read"
                 {:path (:path msg)
                  :file (slurp (File. project-path (:path msg)))}
                 "save"
                 (save-file (:path msg) (:file msg))
                 {:error "Unrecognised command."})]
      (assoc result :command (:command msg))))

