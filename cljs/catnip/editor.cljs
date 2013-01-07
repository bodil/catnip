(ns catnip.editor
  (:use-macros [catnip.requirejs :only [require]])
  (:require [jayq.core :as j :refer [$]]))

(require [ace.editor :only [Editor]]
         [ace.virtual_renderer :only [VirtualRenderer]]
         [ace.multi_select :only [MultiSelect]])

(def ^:private editor (atom nil))

(defn update-theme [editor]
  (let [body ($ "body")]
    (cond
     (j/has-class body :theme-light)
     (.setTheme editor "ace/theme/chrome")
     (j/has-class body :theme-dark)
     (.setTheme editor "ace/theme/tomorrow_night_eighties"))))

(defn create-editor [element]
  (let [ed (Editor. (VirtualRenderer. element))]
    (doto ed
      (MultiSelect.)
      (.setDisplayIndentGuides false)
      (update-theme)
      (.resize)
      (.focus))
    (j/on ($ js/window) "resize" #(.resize ed))
    (reset! editor ed)))

(defn set-session [session]
  (.setSession @editor session))
