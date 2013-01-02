(ns catnip.session
  (:use-macros [catnip.requirejs :only [require]]))

(require [ace.edit_session :only [EditSession]]
         [ace.mode.clojure :only [Mode]]
         [ace.undomanager :only [UndoManager]])

(defn create-session [path content]
  (let [mode (Mode.)
        session (EditSession. content mode)]
    (doto session
      (.setUndoManager (UndoManager.))
      (.setUseSoftTabs true)
      (.setTabSize 2)
      (aset "bufferName" path)
      (aset "dirty" false)
      (.on "change" #(aset session "dirty" true)))))
