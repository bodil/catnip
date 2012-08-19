;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.profile
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(def default-profile
  {:snippets
   {"testfn" "(with-test\n  (defn )\n  (is ))\n"}
   :default-browser-url "/intro.html"})

(defn- profile-path []
  (let [path (io/file (System/getProperty "user.home") ".catnip")]
    (.mkdirs path)
    (io/file path "profile.clj")))

(defn save-profile [profile]
  (spit (profile-path) (with-out-str (pprint/pprint profile)))
  profile)

(defn read-profile []
  (let [path (profile-path)]
    (if (.exists path)
      (merge default-profile (read-string (slurp (profile-path))))
      (save-profile default-profile))))
