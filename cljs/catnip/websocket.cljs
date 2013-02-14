(ns catnip.websocket
  (:use-macros [catnip.requirejs :only [require]])
  (:require [redlobster.events :as e]
            [catnip.dom :as dom]))

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
      0 (let [queue (.-__catnip_queue this)]
        (set! (.-__catnip_queue this)
              (if queue (conj queue message)
                  (vector message))))
      1 (.send this message)
      (.error js/console "send: socket not open"))))

(dom/extend-as-emitter WebSocket)

(defn web-socket [url]
  (let [socket (WebSocket. url)]
    (e/on socket :open
          (fn []
            (.debug js/console "socket connected:" url)
            (when-let [queue (.-__catnip_queue socket)]
              (set! (.-__catnip_queue socket) nil)
              (doseq [message queue]
                (send socket message)))))
    socket))
