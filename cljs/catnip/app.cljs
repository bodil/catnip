(ns catnip.app
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [defer]])
  (:use [catnip.editor :only [create-editor]]
        [catnip.session :only [load-buffer]])
  (:require [catnip.dom :as dom]
            [catnip.socket :as socket]))

(defn main []
  (dom/add-class! (dom/q "body")
                  (str "theme-" (or window/CatnipProfile.theme "light")))

  (socket/connect)
  (create-editor (dom/id "editor"))
  (load-buffer "project.clj")

  (dom/remove-class! (dom/q "body") "loading"))

(defer (main))
