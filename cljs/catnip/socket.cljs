(ns catnip.socket
  (:use-macros [catnip.requirejs :only [require]]
               [redlobster.macros :only [promise]])
  (:require [redlobster.events :as e]
            [redlobster.promise :as p]
            [catnip.websocket :as ws]))

(def ^:private socket (atom nil))
(def ^:private emitter (atom (e/event-emitter)))
(def ^:private tag-counter (atom 0))

(defn- mktag []
  (swap! tag-counter inc))

(defn- broadcast-message [e]
  (.debug js/console "incoming msg:" (.-originalEvent.data e))
  (let [event
        (-> (.-originalEvent.data e)
            (JSON/parse)
            (js->clj :keywordize-keys true))
        ]
    (e/emit @emitter "message" event)))

(defn connect []
  (let [url (str "ws://" window/location.host "/repl")
        s (ws/web-socket url)]
    (e/on s "message" broadcast-message)
    (reset! socket s)))

(defn- send-message
  [message tag]
  (let [message (-> (assoc message :tag tag)
                    (clj->js)
                    (JSON/stringify))]
    (.debug js/console "send:" message)
    (ws/send @socket message)))

(defn send [message]
  (promise
   (let [tag (mktag)]
     (e/on @emitter "message"
           (fn response-listener [event]
             (when (= tag (:tag event))
               (e/remove-listener @emitter "message" response-listener)
               (realise event))))
     (e/on @socket "close" #(realise-error nil))
     (send-message message tag))))
