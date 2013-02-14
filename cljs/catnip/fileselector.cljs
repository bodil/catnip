(ns catnip.fileselector
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [promise]]
               [pylon.macros :only [defclass]]
               [dommy.template-compile :only [deftemplate]])
  (:require [jayq.core :as j :refer [$]]
            [redlobster.events :as e]
            [redlobster.promise :as p]
            [pylon.classes]
            [catnip.dom :as dom :refer [append! replace!]]
            [catnip.keybindings :as kb]
            [clojure.string :as string]))

(defn- assoc-last [v i]
  (assoc v (dec (count v)) i))

(defn- merge-highlight [nodes]
  (loop [r [] nodes nodes]
    (let [this (last r) next (first nodes)]
      (cond
       (not (seq nodes)) r

       (nil? this)
       (recur (conj r next) (rest nodes))

       (and (string? this) (string? next))
       (recur (assoc-last r (str this next)) (rest nodes))

       (and (vector? this) (vector? next))
       (recur (assoc-last r [(first this) (str (second this) (second next))])
              (rest nodes))

       :else
       (recur (conj r next) (rest nodes))
       ))))

(defn- highlight-filter [filter node]
  (loop [r [] filter filter node node]
    (cond
     (not (seq node)) (merge-highlight r)
     (= (first filter) (first node))
     (recur (conj r [:span.filter (str (first node))]) (rest filter) (rest node))
     :else
     (recur (conj r (str (first node))) filter (rest node)))))


(defn- highlighted-node [filter file]
  (apply vector (concat [:li] (if filter (highlight-filter filter file) [file]))))

(defn- order-by-history [l history]
  (let [lset (apply hash-set l)
        hset (apply hash-set history)]
    (if (seq history)
      (concat
       (filter #(contains? lset %) history)
       (remove #(contains? hset %) l))
      l)))

(def ^:private filter-cache
  (memoize
   (fn [f]
     (js/RegExp. (string/join ".*" (string/split f #"(?:)")) "i"))))

(defn- filter-match [f v]
  (let [re (filter-cache f)]
    (re-find re v)))

(defn- install-box []
  (append! ($ "body")
           [:div.file-selector
            [:div.viewport
             [:ul]]]))

(defn- install-input []
  (append! ($ "body") [:input.file-selector {:type "text"}]))

(defclass FileSelector
  (defn constructor [file-set buffer-history filter]
    (set! @.promise (p/promise))
    (set! @.file-set file-set)
    (set! @.files (order-by-history file-set buffer-history))
    (set! @.buffer-history buffer-history)
    (set! @.active-filter "")
    (set! @.box (install-box))
    (set! @.viewport (j/children @.box "div"))
    (set! @.list (j/find @.box "ul"))
    (set! @.input (install-input))
    (@.populate-list)
    (set! @.page-size (/ (j/height @.box)
                        (j/height (j/children @.list "li:first-child"))))
    (e/on js/window :resize @.on-resize)
    (doto @.input
      (e/on :keydown @.on-key-down)
      (e/on :keyup @.on-filter-change)
      (e/on :blur @.close)
      (.focus))
    (j/add-class @.box "fade-in")
    (@.activate (if (> (count buffer-history) 1) 1 0) 200)

    (set! @.keymap
          {"up" @.up
           "down" @.down
           "pageup" @.page-up
           "pagedown" @.page-down
           "home" @.top
           "end" @.bottom
           "left" :swallow
           "right" :swallow
           "return" @.select
           "tab" @.select
           "esc" @.abort
           "C-g" @.abort
           "all" #(.log js/console "plonk" (kb/event-str %))}))

  (defn on-resize [event]
    (@.scroll-to @.active-node))

  (defn on-filter-change [event]
    (let [val (j/val @.input)]
      (when (not= val @.active-filter)
        (@.apply-filter val))))

  (defn on-key-down [event]
    (kb/delegate event @.keymap))

  (defn up [e]
    (j/prevent e)
    (@.activate (if (zero? @.active)
                  (dec (count @.files))
                  (dec @.active))))

  (defn down [e]
    (j/prevent e)
    (@.activate (if (= @.active (dec (count @.files)))
                      0
                      (inc @.active))))

  (defn page-up [e]
    (j/prevent e)
    (@.activate (max (- @.active @.page-size) 0)))

  (defn page-down [e]
    (j/prevent e)
    (@.activate (min (+ @.active @.page-size)
                     (dec (count @.files)))))

  (defn top [e]
    (j/prevent e)
    (@.activate 0))

  (defn bottom [e]
    (j/prevent e)
    (@.activate (dec (count @.files))))

  (defn select [e]
    (j/prevent e)
    (let [active (nth @.files @.active)]
      (if (and active (pos? (count @.files)))
        (p/realise @.promise active)
        (p/realise-error @.promise nil)))
    (@.close))

  (defn abort [e]
    (j/prevent e)
    (p/realise-error @.promise nil)
    (@.close))

  (defn close [e]
    (when e (j/prevent e))
    (j/fade-out @.box
     200 (fn []
           (j/remove @.box)
           (j/remove @.input)
           (e/remove-listener js/window :resize @.on-resize))))

  (defn apply-filter [f]
    (let [file-set @.file-set
          last-active (get @.files @.active)]
      (set! @.active-filter f)
      (set! @.files (order-by-history
                     (if f (filter (partial filter-match f) file-set) file-set)
                     @.buffer-history))
      (@.populate-list)
      (@.activate (if f 0 (max 0 (.indexOf @.files last-active))))))

  (defn populate-list []
    (set! @.list
          (replace! @.list
                    [:ul (map (partial highlighted-node @.filter) @.files)]))
    (set! @.nodes (j/children @.list "li")))

  (defn activate [index & [speed]]
    (let [speed (or speed 50)
          node (get @.nodes index)
          prev @.active-node]
      (when prev (j/remove-class prev "active"))
      (set! @.active index)
      (set! @.active-node node)
      (j/add-class node "active")
      (when (not @.reposition-timeout)
        (@.on-reposition-timeout speed)
        (set! @.reposition-timeout
              (js/setTimeout @.on-reposition-timeout (* speed 2))))))

  (defn on-reposition-timeout [speed]
    (set! @.reposition-timeout nil)
    (.scroll-to this @.active-node speed))

  (defn scroll-to [node speed]
    (let [vp @.viewport
          pos (+ (:top (j/position node)) (/ (j/height node) 2))
          vp-offset (/ (j/height @.box) 2)
          new-pos (+ (j/scroll-top vp) (- pos vp-offset))]
      (if speed
        (j/anim vp {:scrollTop new-pos} speed)
        (j/scroll-top vp new-pos)))))

(defn file-selector [file-set buffer-history filter]
  (.-promise (FileSelector. file-set buffer-history filter)))
