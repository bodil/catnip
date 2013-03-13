(ns catnip.history
  (:require [catnip.dom :as dom]))

(defprotocol IHistory
  (-push [this entry])
  (-forward [this])
  (-back [this current]))

(defn- push-history [entry state]
  (if (= entry (last (:history state)))
    state
    (-> state
        (update-in [:history] #(conj % entry))
        (assoc :pos 0))))

(defn- history-back [current state]
  (if (> (:pos state) (- (count (:history state))))
    (let [temp (or (:temp state) current)
          pos (dec (:pos state))]
      (assoc state :pos pos :temp temp))
    state))

(defn- history-forward [state]
  (if (neg? (:pos state))
    (let [pos (inc (:pos state))
          temp (when-not (zero? pos) (:temp state))]
      (assoc state :pos pos :temp temp))
    state))

(defrecord History [state]
  IHistory
  (-push [this entry]
    (swap! state (partial push-history entry)))

  (-forward [this]
    (let [temp (:temp @state)
          state (swap! state history-forward)]
      (if (zero? (:pos state)) temp
          (get (:history state) (+ (count (:history state)) (:pos state))))))

  (-back [this current]
    (let [state (swap! state (partial history-back current))]
      (get (:history state) (+ (count (:history state)) (:pos state))))))

(defn history []
  (History. (atom {:history []
                   :pos 0
                   :temp nil})))
