(ns catnip.app
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [defer]])
  (:require [catnip.dom :as dom]
            [catnip.socket :as socket]
            [catnip.commands :as cmd :refer [defcommand defkey]]
            [catnip.keymap :as keymap]
            [catnip.editor :refer [create-editor]]
            [catnip.session :refer [load-buffer]]
            [catnip.repl :as repl :refer [create-repl]]
            [redlobster.events :as e]))

(defer
  (dom/add-class! (dom/q "body")
                  (str "theme-" (or window/CatnipProfile.theme "light")))

  (socket/connect)
  (create-repl (dom/id "repl-input")
               (dom/id "repl-display")
               (dom/id "repl-prompt"))
  (create-editor (dom/id "editor"))
  (load-buffer "project.clj")

  (e/on js/window :keydown (cmd/event-handler nil :global))

  (keymap/setup)

  (dom/remove-class! (dom/q "body") "loading"))
