(ns catnip.app
  (:use-macros [catnip.requirejs :only [require]]
               [jayq.macros :only [ready]]
               [redlobster.macros :only [await]])
  (:use [catnip.editor :only [create-editor]]
        [catnip.session :only [create-session load-buffer]]
        [catnip.fileselector :only [file-selector]])
  (:require [jayq.core :as j :refer [$]]
            [catnip.socket :as socket]
            [redlobster.promise :as p]))

(defn main []
  (j/add-class ($ "body") (str "theme-" (or window/CatnipProfile.theme "light")))

  (socket/connect)
  (create-editor (.getElementById js/document "editor"))
  (load-buffer "project.clj")

  (j/remove-class ($ "body") "loading")

  (js/setTimeout
   #(await (file-selector ["Twilight Sparkle"
                           "Pinkie Pie"
                           "Rainbow Dash"
                           "Rarity"
                           "Fluttershy"
                           "Applejack"] ["Rarity" "Rainbow Dash"] nil)
           (.log js/console "selected" result)
           (.log js/console "aborted"))
   1000))

(ready (main))
