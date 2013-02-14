(ns catnip.dom
  (:require [jayq.core :as j :refer [$]]
            [redlobster.events :as e]
            [dommy.template :as t]))

(extend-type js/Element
  e/IEventEmitter
  (on [this event listener]
    (.on ($ this) (e/unpack-event event) listener))
  (once [this event listener]
    (.one ($ this) (e/unpack-event event) listener))
  (remove-listener [this event listener]
    (.off ($ this) (e/unpack-event event) listener))
  (remove-all-listeners [emitter event]
    (throw "DOMElement doesn't support the remove-all-listeners method."))
  (emit [this event args]
    (.triggerHandler ($ this) (e/unpack-event event) (into-array args))))

(extend-type js/Window
  e/IEventEmitter
  (on [this event listener]
    (.on ($ this) (e/unpack-event event) listener))
  (once [this event listener]
    (.one ($ this) (e/unpack-event event) listener))
  (remove-listener [this event listener]
    (.off ($ this) (e/unpack-event event) listener))
  (remove-all-listeners [emitter event]
    (throw "DOMElement doesn't support the remove-all-listeners method."))
  (emit [this event args]
    (.triggerHandler ($ this) (e/unpack-event event) (into-array args))))

(extend-type js/jQuery
  e/IEventEmitter
  (on [this event listener]
    (.on this (e/unpack-event event) listener))
  (once [this event listener]
    (.one this (e/unpack-event event) listener))
  (remove-listener [this event listener]
    (.off this (e/unpack-event event) listener))
  (remove-all-listeners [emitter event]
    (throw "jQuery doesn't support the remove-all-listeners method."))
  (emit [this event args]
    (.triggerHandler this (e/unpack-event event) (into-array args))))

(defn html [hiccup]
  ($ (t/node hiccup)))

(defn append! [$el hiccup]
  (let [new-el (html hiccup)]
    (j/append $el new-el)
    new-el))

(defn replace! [$el hiccup]
  (let [$new ($ (html hiccup))]
    (.replaceWith $el $new)
    $new))
