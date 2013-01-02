(ns catnip.editor
  (:use-macros [catnip.requirejs :only [require]])
  (:require [jayq.core :as j :refer [$]]))

(require [ace.editor :only [Editor]]
         [ace.virtual_renderer :only [VirtualRenderer]]
         [ace.multi_select :only [MultiSelect]])

(defn update-theme [editor]
  (let [body ($ "body")]
    (cond
     (j/has-class body :theme-light)
     (.setTheme editor "ace/theme/chrome")
     (j/has-class body :theme-dark)
     (.setTheme editor "ace/theme/tomorrow_night_eighties"))))

(defn create-editor [element]
  (let [editor (Editor. (VirtualRenderer. element))]
    (doto editor
      (MultiSelect.)
      (.setDisplayIndentGuides false)
      (update-theme)
      (.resize)
      (.focus))
    (j/on ($ js/window) "resize" #(.resize editor))
    editor))
