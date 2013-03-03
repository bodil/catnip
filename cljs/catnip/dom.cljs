(ns catnip.dom
  (:require [redlobster.events :as e]
            [clojure.string :as s]
            [goog.dom]
            [goog.dom.classes]
            [goog.dom.forms]
            [goog.events]
            [goog.style]
            [crate.core :as crate]))

;; Red Lobster EventEmitter impl for DOM elements

(defn- vendor-mangle [un webkit moz ie o]
  (cond
   goog.userAgent.GECKO moz
   goog.userAgent.IE ie
   goog.userAgent.OPERA o
   goog.userAgent.WEBKIT webkit
   :else un))

(def ^:private mangled-events
  {:transitionend (vendor-mangle "transitionEnd"
                                 "webkitTransitionEnd"
                                 "transitionend"
                                 "msTransitionEnd"
                                 "oTransitionEnd")})

(defn- mangle-event
  "Performs vendor mangling of an event name when appropriate."
  [e]
  (name (or (mangled-events (keyword e)) (keyword e))))

(defn extend-as-emitter [type]
  (extend-type type
    e/IEventEmitter
    (on [this event listener]
      (goog.events/listen this (mangle-event event) listener))
    (once [this event listener]
      (goog.events/listenOnce this (mangle-event event) listener))
    (remove-listener [this event listener]
      (goog.events/unlisten this (mangle-event event) listener))
    (remove-all-listeners [emitter event]
      (goog.events/removeAll emitter event))
    (emit [this event arg]
      (goog.events/fireListeners this (mangle-event event) false arg))))

(doseq [type [js/Element js/Window]] (extend-as-emitter type))

;; Make NodeList seqable and lookupable

(defn- key-in-array? [array key]
  (and (number? key) (not (neg? key)) (< key (.-length array))))

(defn- lazify-nodelist
  ([list]
     (lazify-nodelist list 0))
  ([list index]
     (when (key-in-array? list index)
       (lazy-seq
        (cons (.item list index)
              (lazy-seq (lazify-nodelist list (inc index))))))))

(extend-type js/NodeList
  ILookup
  (-lookup
    ([list key]
       (when (key-in-array? list key) (.item list key)))
    ([list key default]
       (if (key-in-array? list key) (.item list key) default)))
  ISeqable
  (-seq [list]
    (lazify-nodelist list)))

;; DOM manipulation et al

(defn html [hiccup]
  (crate/html hiccup))

(defn append!
  "Appends a DOM node, a string or a sequence of either to a parent node."
  [parent children]
  (goog.dom/append parent (if (sequential? children)
                            (into-array children)
                            children))
  children)

(defn remove!
  "Removes a DOM node or a sequence of DOM nodes from their respective parents."
  [nodes]
  (if (sequential? nodes)
    (doseq [node nodes] (goog.dom/removeNode node))
    (goog.dom/removeNode nodes)))

(defn replace!
  "Removes a DOM node from the tree and inserts another in its place."
  [old-node new-node]
  (goog.dom/replaceNode new-node old-node)
  new-node)

(defn- unwrap-class [class]
  (if (sequential? class)
    (map unwrap-class class)
    (if (keyword? class) (name class) class)))

(defn add-class!
  "Adds a class or sequence of classes to a DOM node."
  [node classes]
  (let [classes (unwrap-class classes)]
    (goog.dom.classes/add
     node (if (sequential? classes) (s/join classes " ") classes))))

(defn remove-class!
  "Removes a class or sequence of classes from a DOM node."
  [node classes]
  (let [classes (unwrap-class classes)]
    (goog.dom.classes/remove
     node (if (sequential? classes) (s/join classes " ") classes))))

(defn toggle-class!
  "Toggles a class or sequence of classes on a DOM node."
  [node classes]
  (let [classes (unwrap-class classes)]
    (if (sequential? classes)
      (doseq [class classes] (goog.dom.classes/toggle node class))
      (goog.dom.classes/toggle node classes))))

(defn has-class?
  "Tests if an element has a given class or a sequence of classes."
  [node classes]
  (let [classes (unwrap-class classes)]
    (if (sequential? classes)
      (every? #(goog.dom.classes/has node %) classes)
      (goog.dom.classes/has node classes))))

(defn q
  "Performs a CSS query, returns the first match."
  ([query]
     (.querySelector js/document query))
  ([root query]
     (.querySelector root query)))

(defn q*
  "Performs a CSS query, returns all matches."
  ([query]
     (.querySelectorAll js/document query))
  ([root query]
     (.querySelectorAll root query)))

(defn id
  "Returns the element with the given ID."
  [id]
  (goog.dom/getElement id))

(defn width
  "Returns the width of an element."
  [node]
  (.-width (goog.style/getSize node)))

(defn height
  "Returns the height of an element."
  [node]
  (.-height (goog.style/getSize node)))

(defn position
  "Returns the position of an element relative to the document,
as a map with `:left` and `:top` keys."
  [node]
  (let [pos (goog.style/getPosition node)]
    {:left (.-x pos) :top (.-y pos)}))

(defn scroll-position
  "Returns an element's scrollbar offsets."
  [node]
  {:left (.-scrollLeft node)
   :top (.-scrollTop node)})

(defn scroll!
  "Scrolls an element to a given offset."
  [node offset]
  (when (contains? offset :left)
    (set! (.-scrollLeft node) (:left offset)))
  (when (contains? offset :top)
    (set! (.-scrollTop node) (:top offset))))

(defn scroll-into-view!
  "Changes the scroll position of container with the minimum amount so
that the content and the borders of the given element become visible.
If the element is bigger than the container, its top left corner will
be aligned as close to the container's top left corner as possible.

If no container is specified, the element's immediate parent is assumed."
  ([element container]
     (goog.style/scrollIntoContainerView element container))
  ([element]
     (scroll-into-view! element (goog.dom/getParentElement element))))

(def ^:private -vendor-prefix
  (vendor-mangle nil "-webkit" "-moz" "-ms" "-o"))

(defn vendor-prefix [property]
  (if (= "-" (first property))
    (if -vendor-prefix (str -vendor-prefix property)
        (subs property 1))
    property))

(defn style!
  "Sets CSS styles on an element. If a style name is prefixed by a dash,
automatically append the applicable vendor prefix."
  [node & pairs]
  (doseq [[style value] (partition 2 pairs)]
    (goog.style/setStyle node (vendor-prefix style) value)))

(defn active-element
  "Get the element that currently has focus."
  []
  (.-activeElement js/document))

(defn value
  "Get the value of an input element."
  [node]
  (goog.dom.forms/getValue node))

(defn value!
  "Set the value of an input element."
  [node value]
  (goog.dom.forms/setValue node value))

(defn text!
  "Set the text content of an element."
  [node text]
  (goog.dom/setTextContent node text))
