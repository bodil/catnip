(ns catnip.session
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [await]])
  (:require [redlobster.promise :as p]
            [catnip.socket :as socket]
            [catnip.editor :as editor]))

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

(defn load-buffer
  ([path line]
     (await
      (socket/send {:fs {:command "read" :path path}})
      (editor/set-session (create-session path (-> result :fs :file)))
      (.error js/console "read failed so hard" path)))
  ([path] (load-buffer path nil)))
