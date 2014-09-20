;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.cljs
  (:require [clojure.java.io :as io]
            [cljs.closure :as cljsc]
            [catnip.paths :as paths])
  (:use [clojure.test]))

(with-test
  (defn extract-cljsbuild [project-file]
    (let [project (read-string project-file)]
      (when (seq? project)
        (loop [project project]
          (cond
           (= :cljsbuild (first project)) (second project)
           (not (seq project)) nil
           :else (recur (rest project)))))))
  (is (= {:ohai "noob"} (extract-cljsbuild
                         "(defproject catnip \"0.hai\" :dependencies [[o/hai \"0.hai\"]] :cljsbuild {:ohai \"noob\"})")))
  (is (nil? (extract-cljsbuild "hai"))))

(defn load-cljsbuild [project-path]
  (let [project-file (io/file project-path "project.clj")]
    (extract-cljsbuild (slurp project-file))))

(with-test
  (defn first-build [project]
    (when (map? project)
      (if-let [builds (:builds project)]
        (first builds))))
  (is (= :ohai (first-build {:builds [:ohai]}))))

(with-test
  (defn path-in-build? [path build]
    (if-let [source-path (:source-path build)]
      (paths/inside? (io/file source-path) (io/file path))
      (boolean (some #(paths/inside? (io/file %)
                                     (io/file path))
                     (:source-paths build)))))
  (is (true? (path-in-build? "src/foo.cljs"
                             {:source-path "src"})))
  (is (true? (path-in-build? "src1/foo.cljs"
                             {:source-paths ["src1" "src2"]})))
  (is (true? (path-in-build? "src2/foo.cljs"
                             {:source-paths ["src1" "src2"]})))
  (is (false? (path-in-build? "src/foo.cljs"
                             {:source-path "test"})))
  (is (false? (path-in-build? "src/foo.cljs"
                              {:source-paths ["src1" "src2"]}))))

(with-test
 (defn builds-for [cljsbuild path]
   (filter (partial path-in-build? path) (:builds cljsbuild)))
 (is (= [{:source-path "src"}
         {:source-path "src/cljs"}]
        (builds-for {:builds [{:source-path "src"}
                              {:source-path "src/cljs"}
                              {:source-path "test"}]}
                    "src/cljs/wibble.cljs"))))

(defn compile-build [build]
  (when-let [srcpath (or (:source-path build)
                         (reify
                           cljsc/Compilable
                           (-compile [_ opts]
                             (mapcat #(cljsc/-compile % opts)
                                     (:source-paths build)))))]
    (when-let [options (:compiler build)]
      (try
        (cljsc/build srcpath options)
        (assoc build :success true :output "")
        (catch Throwable e
          (assoc build :success false :error (.getMessage e)))))))

(defn cljs-compile [project-path path]
  {:result (map compile-build (builds-for (load-cljsbuild project-path) path))
   :path path})
