(ns catnip.keymap
  (:require [catnip.commands :refer [defcommand defkey]]))

(defn setup []
  (defkey :global "C-r" "toggle-repl")

  (defkey :global "C-f" "open-file")
  (defkey :global "C-s" "save-buffer")

  (defkey :repl "return" "repl-eval")
  (defkey :repl "up" "repl-history-back")
  (defkey :repl "down" "repl-history-forward"))
