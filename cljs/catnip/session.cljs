(ns catnip.session
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [waitp let-realised]])
  (:require [redlobster.promise :as p]
            [catnip.socket :as socket]
            [catnip.editor :as editor]
            [catnip.commands :refer [defcommand]]
            [catnip.fileselector :refer [file-selector]]))

(require [ace.edit_session :only [EditSession]]
         [ace.mode.clojure :only [Mode]]
         [ace.undomanager :only [UndoManager]])

(def buffer-history (atom ()))

(defn push-buffer-history [path]
  (swap! buffer-history
         (fn [history]
           (cons path (remove #(= % path) history)))))

(defn create-session [path content]
  (let [mode (Mode.)
        session (EditSession. content mode)]
    (doto session
      (.setUndoManager (UndoManager.))
      (.setUseSoftTabs true)
      (.setTabSize 2)
      (.on "change" #(set! (.-dirty session) true)))
    (set! (.-bufferName session) path)
    (set! (.-dirty session) false)
    session))

(defn load-buffer
  ([path line]
     (waitp
      (socket/send {:fs {:command "read" :path path}})
      (fn [result]
        (editor/set-session (create-session path (-> result :fs :file)))
        (push-buffer-history path)
        (editor/focus)
        (realise true))
      (fn [_]
        (.error js/console "read failed so hard" path)
        (realise-error false))))
  ([path] (load-buffer path nil)))

(defcommand "open-file"
    (fn []
      ;; TODO: Wrap this in a spinner-until-realised thing?
      (let-realised
       [files (socket/send {:fs {:command "files"}})]
       (let-realised
        [selected (file-selector (-> @files :fs :files) @buffer-history)]
        (load-buffer @selected)))))
