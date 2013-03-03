(ns catnip.commands
  (:require [catnip.dom :as dom]
            [catnip.keybindings :as kb]))

(def commands (atom {}))
(def keymaps (atom {:global {}
                   :editor {}
                   :repl {}}))

(defn defcommand [name func & [doc]]
  (swap! commands #(assoc % name {:function func
                                  :doc doc})))

(defn defkey [keymap binding command]
  (swap! keymaps (fn [keymaps]
                   (let [new-keymap (assoc (keymaps keymap) binding command)]
                     (assoc keymaps keymap new-keymap)))))

(defn command [context name]
  (if-let [cmd (@commands name)]
    ((:function cmd) context)
    (.error js/console (str "Unknown command " name))))

(defn doc [name]
  (when-let [cmd (@commands name)]
    (:doc cmd)))

(defn event-handler [context & maps]
  (fn [event]
    (when-let [cmd (kb/keymap-lookup (kb/event-str event)
                                     (map @keymaps maps))]
      (.preventDefault event)
      (command context cmd))))
