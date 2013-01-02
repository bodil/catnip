(ns catnip.websocket
  (:use-macros [catnip.requirejs :only [require]])
  (:require [redlobster.events :as e]
            [jayq.core :as j :refer [$]]))

(def WebSocket (or window/MozWebSocket window/WebSocket))

(defprotocol IWebSocket
  (close [this])
  (close [this code reason])
  (send [this message]))

(extend-type WebSocket
  IWebSocket
  (close [this]
    (.close this))
  (close [this code reason]
    (.close this code reason))
  (send [this message]
    (case (.-readyState this)
      0 (let [queue (aget this "__catnip_queue")]
        (aset this "__catnip_queue"
              (if queue (conj queue message)
                  (vector message))))
      1 (.send this message)
      (.error js/console "send: socket not open")))
  e/IEventEmitter
  (on [this event listener]
    (j/on ($ this) (e/unpack-event event) listener))
  (once [this event listener]
    (j/one ($ this) (e/unpack-event event) listener))
  (remove-listener [this event listener]
    (j/off ($ this) (e/unpack-event event) listener))
  (remove-all-listeners [emitter event]
    (throw "WebSocket doesn't support the remove-all-listeners method."))
  (emit [this event args]
    (.triggerHandler ($ this) event (into-array args))))

(defn web-socket [url]
  (let [socket (WebSocket. url)]
    (e/on socket :open
          (fn []
            (.debug js/console "socket connected:" url)
            (when-let [queue (aget socket "__catnip_queue")]
              (aset socket "__catnip_queue" nil)
              (doseq [message queue]
                (send socket message)))))
    socket))
