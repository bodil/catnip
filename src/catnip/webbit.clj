;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.webbit
  (:require [clojure.string :as str])
  (:import [org.webbitserver HttpHandler]
           [org.webbitserver.handler StaticFileHandler]
           [java.net URI]))

(defn- transform-path [path from to]
  (str/replace path from to))

(defn- transform-url [url from to]
  (let [uri (URI. url)
        path (.getPath uri)
        new-path (transform-path path from to)]
       (if (= path new-path)
           url
           (.toString (URI. 
             (.getScheme uri)
             (.getUserInfo uri)
             (.getHost uri)
             (.getPort uri)
             new-path
             (.getQuery uri)
             (.getFragment uri))))))

; Examples:
#_(transform-url "/files/default.htm" #"/files(/.*)" "$1")
#_(transform-url "http://localhost:11/files/default.htm" #"/files(/.*)" "$1")

(defn- transform-req [req from to]
  (let [uri (.uri req)
        new-uri (transform-url uri from to)
        transformed (not= new-uri uri)]
       (when transformed
         (.uri req new-uri))
       transformed))

(defn- path-transform-handler [from to handler]
  (proxy [HttpHandler] []
    (handleHttpRequest [req res ctl]
      (if (transform-req req from to)
        (.handleHttpRequest handler req res ctl)
        (.nextHandler ctl)))))

(defn relative-file-handler [mount-point local-path]
  (let [pattern (re-pattern (str mount-point "(/.*)"))]
    (path-transform-handler pattern "$1" (StaticFileHandler. local-path))))
