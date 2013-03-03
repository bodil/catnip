(ns catnip.socket
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [promise]])
  (:require [redlobster.events :as e]
            [redlobster.promise :as p]
            [catnip.websocket :as ws]
            [cljs.reader :refer [read-string]]))

(def ^:private socket (atom nil))
(def ^:private emitter (atom (e/event-emitter)))
(def ^:private tag-counter (atom 0))

(defn- mktag []
  (swap! tag-counter inc))

(defn- broadcast-message [e]
  (.debug js/console "incoming msg:" (.-event_.data e))
  (let [event (read-string (.-event_.data e))]
    (e/emit @emitter "message" event)))

(defn connect []
  (let [url (str "ws://" window/location.host "/repl")
        s (ws/web-socket url)]
    (e/on s "message" broadcast-message)
    (reset! socket s)))

(defn- send-message
  [message tag]
  (let [message (str (assoc message :tag tag))]
    (.debug js/console "send:" message)
    (ws/send @socket message)))

(defn on-message [listener]
  (e/on @emitter "message" listener))

(defn off-message [listener]
  (e/remove-listener @emitter "message" listener))

(defn send [message]
  (promise
   (let [tag (mktag)]
     (on-message
      (fn response-listener [event]
        (when (= tag (:tag event))
          (off-message response-listener)
          (realise event))))
     (e/on @socket "close" #(realise-error nil))
     (send-message message tag))))
