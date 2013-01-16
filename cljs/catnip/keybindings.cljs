(ns catnip.keybindings
  (:require [clojure.string :as string]
            [jayq.core :as j]))

(def ^:private special-keys
  {8 "backspace", 9 "tab", 13 "return", 19 "pause",
   20 "capslock", 27 "esc", 32 "space", 33 "pageup",
   34 "pagedown", 35 "end", 36 "home", 37 "left",
   38 "up", 39 "right", 40 "down", 45 "insert",
   46 "del", 96 "0", 97 "1", 98 "2", 99 "3", 100 "4",
   101 "5", 102 "6", 103 "7", 104 "8", 105 "9",
   106 "*", 107 "+", 109 "-", 110 ".", 111  "/",
   112 "f1", 113 "f2", 114 "f3", 115 "f4", 116 "f5",
   117 "f6", 118 "f7", 119 "f8", 120 "f9", 121 "f10",
   122 "f11", 123 "f12", 144 "numlock", 145 "scroll",
   188 ",", 190 ".", 191 "/"})

(def ^:private modifier-keys
  {16 "shift", 17 "ctrl", 18 "alt", 224 "meta"})

(defn- key-name [k]
  (string/lower-case (String/fromCharCode k)))

(defn- prepend [s k v]
  (if k (str v "-" s) s))

(defn event-str [e]
  (let [k (.-which e)]
   (if (modifier-keys k) nil
       (let [k (or (special-keys k) (key-name k))]
         (-> k
             (prepend (.-shiftKey e) "S")
             (prepend (.-ctrlKey e) "C")
             (prepend (.-altKey e) "M")
             (prepend (.-metaKey e) "⌘"))))))

(defn binding? [e binding]
  (= (event-str e) binding))

(defn keymap-lookup [key keymaps]
  (when (seq keymaps)
    (let [keymap (first keymaps)]
      (cond
       (keymap key) (keymap key)
       (keymap "all") (keymap "all")
       :else (recur key (rest keymaps))))))

(defn delegate [e & keymaps]
  (when-let [k (event-str e)]
    (when-let [func (keymap-lookup k keymaps)]
      (if (= func :swallow)
        (j/prevent e)
        (func e)))))
