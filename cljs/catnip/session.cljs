(ns catnip.session
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [waitp let-realised]])
  (:require [redlobster.promise :as p]
            [catnip.socket :as socket]
            [catnip.editor :as editor]
            [catnip.commands :refer [defcommand]]
            [catnip.fileselector :refer [file-selector]]
            [catnip.path :as path]))

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
     (let-realised [result (socket/send {:fs {:command "read" :path path}})]
       (let [session (create-session path (-> @result :fs :file))]
         (editor/set-session session)
         (push-buffer-history path)
         (editor/focus)
         session)))
  ([path] (load-buffer path nil)))

(defn save-buffer [session]
  (waitp (socket/send {:fs {:command "save"
                            :path (.-bufferName session)
                            :file (.getValue session)}})
    #(if (get-in % [:fs :success])
       (realise (:fs %))
       (realise-error (-> % :fs :error)))
    #(realise-error %)))

(defn eval-buffer [session]
  (socket/send {:eval (.getValue session)
                :target :node
                :path (.-bufferName session)}))

(defcommand "open-file"
    (fn []
      ;; TODO: Wrap this in a spinner-until-realised thing?
      (let-realised [files (socket/send {:fs {:command "files"}})]
        (let-realised [selected (file-selector (-> @files :fs :files) @buffer-history)]
          (load-buffer @selected)))))

(defcommand "save-buffer"
  (fn []
    (let [session (editor/active-session)]
      (let-realised [result (save-buffer session)]
        (let [path (:path @result)
              ext (path/file-extension path)]
          (cond
           (#{"cljs" "clj"} ext) (eval-buffer session)))))))
