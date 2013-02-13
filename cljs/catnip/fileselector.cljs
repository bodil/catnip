(ns catnip.fileselector
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [promise]]
               [pylon.macros :only [defclass]])
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
    (doto this
      (aset "promise" (p/promise))
      (aset "fileSet" file-set)
      (aset "files" (order-by-history file-set buffer-history))
      (aset "bufferHistory" buffer-history)
      (aset "activeFilter" "")
      (aset "box" (install-box))
      (aset "viewport" (j/children (.-box this) "div"))
      (aset "list" (j/find (.-box this) "ul"))
      (aset "input" (install-input))
      (.populateList)
      (aset "pageSize" (/ (j/height (.-box this))
                          (j/height (j/children (.-list this)
                                                "li:first-child")))))
    (e/on js/window :resize (.-onResize this))
    (doto (.-input this)
      (e/on :keydown (.-onKeyDown this))
      (e/on :keyup (.-onFilterChange this))
      (e/on :blur (.-close this))
      (.focus))
    (j/add-class (.-box this) "fade-in")
    (.activate this (if (> (count buffer-history) 1) 1 0) 200)

    (aset this "keymap"
          {"up" (.-up this)
           "down" (.-down this)
           "pageup" (.-pageUp this)
           "pagedown" (.-pageDown this)
           "home" (.-top this)
           "end" (.-bottom this)
           "left" :swallow
           "right" :swallow
           "return" (.-select this)
           "tab" (.-select this)
           "esc" (.-abort this)
           "C-g" (.-abort this)
           "all" #(.log js/console "plonk" (kb/event-str %))}))

  (defn onResize [event]
    (.scrollTo this (.-activeNode this)))

  (defn onFilterChange [event]
    (let [val (j/val (.-input this))]
      (when (not= val (.-activeFilter this))
        (.applyFilter this val))))

  (defn onKeyDown [event]
    (kb/delegate event (.-keymap this)))

  (defn up [e]
    (j/prevent e)
    (.activate this (if (zero? (.-active this))
                      (dec (count (.-files this)))
                      (dec (.-active this)))))

  (defn down [e]
    (j/prevent e)
    (.activate this (if (= (.-active this) (dec (count (.-files this))))
                      0
                      (inc (.-active this)))))

  (defn pageUp [e]
    (j/prevent e)
    (.activate this (max (- (.-active this) (.-pageSize this)) 0)))

  (defn pageDown [e]
    (j/prevent e)
    (.activate this (min (+ (.-active this) (.-pageSize this))
                         (dec (count (.-files this))))))

  (defn top [e]
    (j/prevent e)
    (.activate this 0))

  (defn bottom [e]
    (j/prevent e)
    (.activate this (dec (count (.-files this)))))

  (defn select [e]
    (j/prevent e)
    (let [active (get (.-files this) (.-active this))]
      (if (and active (pos? (count (.-files this))))
        (p/realise (.-promise this) active)
        (p/realise-error (.-promise this) nil)))
    (.close this))

  (defn abort [e]
    (j/prevent e)
    (p/realise-error (.-promise this) nil)
    (.close this))

  (defn close [e]
    (when e (j/prevent e))
    (j/fade-out (.-box this)
     200 (fn []
           (j/remove (.-box this))
           (j/remove (.-input this))
           (e/remove-listener js/window :resize (.-onResize this)))))

  (defn applyFilter [f]
    (let [file-set (.-fileSet this)
          last-active (get (.-files this) (.-active this))]
      (doto this
        (aset "activeFilter" f)
        (aset "files"
              (order-by-history
               (if f (filter (partial filter-match f) file-set) file-set)
               (.-bufferHistory this))))
      (.populateList this)
      (.activate this (if f 0 (max 0 (.indexOf (.-files this) last-active))))))

  (defn populateList []
    (replace!
     (.-list this)
     (if (zero? (count (.-files this)))
       [:li "No files match filter "
        [:span.filter (.-filter this)]]
       (map (partial highlighted-node (.-filter this)) (.-files this))))
    (aset this "nodes" (j/children (.-list this) "li")))

  (defn activate [index & [speed]]
    (let [speed (or speed 50)
          node (get (.-nodes this) index)
          prev (.-activeNode this)]
      (when prev (j/remove-class prev "active"))
      (aset this "active" index)
      (aset this "activeNode" node)
      (j/add-class node "active")
      (when (not (.-repositionTimeout this))
        (.onRepositionTimeout this speed)
        (aset this "repositionTimeout"
              (js/setTimeout (.-onRepositionTimeout this) (* speed 2))))))

  (defn onRepositionTimeout [speed]
    (aset this "repositionTimeout" nil)
    (.scrollTo this (.-activeNode this) speed))

  (defn scrollTo [node speed]
    (let [vp (.-viewport this)
          pos (+ (:top (j/position node)) (/ (j/height node) 2))
          vp-offset (/ (j/height (.-box this)) 2)
          new-pos (+ (j/scroll-top vp) (- pos vp-offset))]
      (if speed
        (j/anim vp {:scrollTop new-pos} speed)
        (j/scroll-top vp new-pos)))))

(defn file-selector [file-set buffer-history filter]
  (.-promise (FileSelector. file-set buffer-history filter)))



;; (defn- build-selector [file-set buffer-history filter]
;;   {:box (install-selector)
;;    :input (install-input)
;;    :files file-set
;;    :buffer-history buffer-history
;;    :filter filter})

;; (defn- page-size [selector]
;;   (Math/round
;;    (/ (j/height (:box @selector))
;;       (j/height (j/find (:box @selector) "li:first-child")))))

;; (defn- node-at [selector index]
;;   (get (:nodes @selector) index))

;; (defn- activate [selector index & [speed]]
;;   (let [speed (or speed 50)]
;;     (j/remove-class (:active-node @selector) "active")
;;     (let [node (node-at selector index)]
;;       (aset! selector
;;              :active index
;;              :active-node node)
;;       (j/add-class node "active")
;;       (when (not (:reposition-timeout @selector))
;;         (scroll-to selector speed)
;;         (aset! )))))

;; (defn file-selector [file-set buffer-history filter]
;;   (promise
;;    (let [selector (atom (build-selector file-set buffer-history filter))]

;;      (defon selector close [event]
;;        (j/prevent event)
;;        (j/fade-out (:box @selector) 200
;;                    (fn []
;;                      (j/remove (:box @selector))
;;                      (j/remove (:input @selector))
;;                      (e/remove-listener js/window :resize
;;                                         (:on-resize @selector))
;;                      (:complete @selector))))

;;      (defon selector on-resize [event]
;;        (.log js/console "ohai screen resized"))

;;      (e/on js/window :resize (:on-resize @selector))
;;      (doto (:input @selector)
;;        ;; (e/on :blur (:close @selector))
;;        (.focus))

;;      (populate-list selector)
;;      (aset! selector :page-size (page-size selector))

;;      (j/add-class (:box @selector) "fade-in"))))
