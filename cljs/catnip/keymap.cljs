(ns catnip.keymap
  (:require [catnip.commands :refer [defcommand defkey]]))

(defn setup []
  (defkey :global "C-f" "open-file"))
