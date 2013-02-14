(ns catnip.app
  (:use-macros [catnip.requirejs :only [require]]
               [jayq.macros :only [ready]])
  (:use [catnip.editor :only [create-editor]]
        [catnip.session :only [load-buffer]])
  (:require [jayq.core :as j :refer [$]]
            [catnip.socket :as socket]))

(defn main []
  (j/add-class ($ "body") (str "theme-" (or window/CatnipProfile.theme "light")))

  (socket/connect)
  (create-editor (.getElementById js/document "editor"))
  (load-buffer "project.clj")

  (j/remove-class ($ "body") "loading"))

(ready (main))
