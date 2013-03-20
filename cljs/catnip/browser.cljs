(ns catnip.browser
  (:require [redlobster.events :as e]
            [catnip.dom :as dom]
            [catnip.editor :as editor]
            [catnip.commands :refer [defcommand]]))

(def ^:private active-browser (atom nil))

(defrecord Browser [frame location-bar refresh-button url])

(defn load-url [browser url]
  (.log js/console (pr-str browser))
  (set! (.-src (:frame browser)) url)
  (reset! (:url browser) url))

(defn- sync-location-bar [browser & [url]]
  (let [url (or url (.-src (:frame browser)))]
    (dom/value! (:location-bar browser) url)
    (reset! (:url browser) url)))

(defn reload
  ([] (reload @active-browser))
  ([browser] (set! (.-src (:frame browser)) @(:url browser))))

(defn create-browser [frame location-bar refresh-button]
  (let [browser (Browser. frame location-bar refresh-button (atom nil))]
    (reset! active-browser browser)
    (e/on frame :load #(sync-location-bar browser))
    (e/on (-> location-bar .-parentElement .-parentElement) :submit
          (fn [e]
            (.preventDefault e)
            (load-url browser (dom/value location-bar))))
    (e/on refresh-button :click #(reload browser))
    (load-url browser (aget window/CatnipProfile "default-browser-url"))))

(defcommand "toggle-browser"
  (fn []
    (dom/toggle-class! js/window.document.body "hide-browser")
    (editor/resize)))
