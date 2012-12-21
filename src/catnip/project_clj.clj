;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.project-clj
  (:require [catnip.filesystem :as fs]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn- project-clj-path []
  (io/file fs/project-path "project.clj"))

(defn read-project-clj []
  (read-string (slurp (project-clj-path))))

(defn project->map [project]
  (apply hash-map (drop 3 project)))

(defn project-key [key project]
  (if (map? project) (project key)
      ((project->map project) key)))

(defn catnip-key [key project]
  (when-let [catnip-conf (project-key :catnip project)]
    (catnip-conf key)))
