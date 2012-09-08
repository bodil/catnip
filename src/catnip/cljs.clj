;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.cljs
  (:require [clojure.java.io :as io]
            [cljs.closure :as cljsc])
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

(defn cljs-compile [project-path]
  (when-let [build (first-build (load-cljsbuild project-path))]
    (when-let [srcpath (:source-path build)]
      (when-let [options (:compiler build)]
        (try
          (cljsc/build srcpath options)
          {:success true :output ""}
          (catch Throwable e
            {:success false :error (.getMessage e)}))))))
