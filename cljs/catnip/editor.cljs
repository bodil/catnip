(ns catnip.editor
  (:use-macros [catnip.requirejs :only [require]])
  (:require [redlobster.events :as e]
            [catnip.dom :as dom]))

(require [ace.editor :only [Editor]]
         [ace.virtual_renderer :only [VirtualRenderer]]
         [ace.multi_select :only [MultiSelect]])

(defn extend-ace-emitter [type]
  (extend-protocol e/IEventEmitter
    type
    (on [emitter event listener]
      (.on emitter (e/unpack-event event) listener))
    (once [emitter event listener]
      (let [event (e/unpack-event event)]
        (.on emitter event (e/wrap-once emitter event listener))))
    (remove-listener [emitter event listener]
      (.removeListener emitter (e/unpack-event event) listener))
    (remove-all-listeners [emitter]
      (throw "ace.lib.event_emitter.EventEmitter doesn't support the `remove-all-listeners` method without an event argument."))
    (remove-all-listeners [emitter event]
      (.removeAllListeners emitter (e/unpack-event event)))
    (listeners [emitter event]
      (throw "ace.lib.event_emitter.EventEmitter doesn't support the `listeners` method."))
    (emit [emitter event data]
      (._emit emitter (e/unpack-event event) data))))

(extend-ace-emitter Editor)

(def ^:private editor (atom nil))

(defn update-theme [editor]
  (let [body (dom/q "body")]
    (cond
     (dom/has-class? body :theme-light)
     (.setTheme editor "ace/theme/chrome")
     (dom/has-class? body :theme-dark)
     (.setTheme editor "ace/theme/tomorrow_night_eighties"))))

(defn create-editor [element]
  (let [ed (Editor. (VirtualRenderer. element))]
    (doto ed
      (MultiSelect.)
      (.setDisplayIndentGuides false)
      (update-theme)
      (.resize)
      (.focus))
    (e/on js/window :resize #(.resize ed))
    (reset! editor ed)))

(defn set-session [session]
  (.setSession @editor session))
