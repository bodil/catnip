;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.paths
  (:require [clojure.java.io :as io])
  (:use [clojure.test])
  (:import [java.io File]))

(defn tempfile [content]
  (let [f (File/createTempFile "clj-test" nil)]
    (spit f content)
    f))

(with-test
  (defn dotfile?
    "Tests if file is a dotfile or inside a dotfile path."
    [f]
    (if f
      (if (re-matches #"\..*" (.getName f))
        true
        (recur (.getParentFile f)))
      false))
  (is (dotfile? (io/file ".gitignore")))
  (is (dotfile? (io/file "/foo/bar/.gitignore")))
  (is (dotfile? (io/file "foo/.git/config")))
  (is (not (dotfile? (io/file "/foo/bar/gazonk")))))

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
  (is (inside-none? [(io/file "/foo/bar") (io/file "/bar/foo")] (io/file "/quux/foo/bar")))
  (is (not (inside-none? [(io/file "/foo/bar/project.clj")]
                         (io/file "/foo/bar/project.clj")))))

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

(with-test
  (defn glob-matcher
    "Make a file matcher function from a glob."
    [glob]
    (if (re-matches #".*\*.*" glob)
      ; If glob looks like a glob, then
      (let [re (re-pattern (clojure.string/replace glob #"\*" ".*"))]
        (if (re-matches #".*/.*" glob)
          ; If glob looks like a path, match the whole thing
          (fn [f] (re-matches re (str f)))
          ; If it has no path delimiter, match only file part
          (fn [f] (re-matches re (.getName f)))))
      ; If not a glob, do a subdir match
      (fn [f] (inside? (io/file glob) f))
      )
    )
  (is ((glob-matcher "foo*bar") (io/file "foobar")))
  (is ((glob-matcher "foo*bar") (io/file "foobazbar")))
  (is (not ((glob-matcher "foo*bar") (io/file "foobarbaz"))))
  (is (not ((glob-matcher "*foo") (io/file "foobar"))))
  (is ((glob-matcher "*foo") (io/file "barfoo")))
  (is ((glob-matcher "foo*") (io/file "/path/foobar")))
  (is (not ((glob-matcher "/foo*") (io/file "foobar"))))
  (is ((glob-matcher "/foo*") (io/file "/foobar")))
  (is ((glob-matcher "/foo*") (io/file "/foo/bar")))
  (is ((glob-matcher "/foo") (io/file "/foo/bar")))
  (is (not ((glob-matcher "/foo") (io/file "/bar/foo")))))
