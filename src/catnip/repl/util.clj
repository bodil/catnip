;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl.util
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.repl :as repl]
            [catnip.filesystem :as fs]
            [clj-stacktrace.core :as stacktrace])
  (:use [clojure.test]))

(with-test
  (defn map-if-key
    "If hash contains key, apply function f to value of key."
    [hash & args]
    (if hash
      (reduce (fn [hash [key f]]
                (if (hash key)
                  (assoc hash key (f (hash key)))
                  hash))
              hash (partition 2 args))
      hash))
  (is (= {:foo 5 :bar 2} (map-if-key {:foo 4 :bar 2} :foo inc)))
  (is (= {:foo 4 :bar 2} (map-if-key {:foo 4 :bar 2} :baz inc)))
  (is (= {:foo 5 :bar 1} (map-if-key {:foo 4 :bar 2} :foo inc :bar dec))))

(defn- annotate-local [e]
  (let [e (if (:cause e)
            (assoc e :cause (annotate-local (:cause e)))
            e)
        localise
        (fn [el]
          (if (= (:file el) "NO_SOURCE_FILE") el
              (if-let [path (fs/ns-as-local-file (:ns el))]
                (assoc el :local path)
                el)))]
    (if (:trace-elems e)
      (assoc e :trace-elems
             (map localise (:trace-elems e))) e)))

(defn pprint-exception [e]
  (annotate-local (stacktrace/parse-exception e)))

(defn stream [s]
  (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. s)))

(with-test
  (defn ppr [el]
    (string/trim (with-out-str (pprint/pprint el))))
  (is (= "1337" (ppr 1337))))

(defn eval-stream [socket path s eval-func]
  (loop [line (.getLineNumber s)
         sexp (read s false :repl-stream-ended)
         results []]
    (if-not (= :repl-stream-ended sexp)
      (let [result (assoc (eval-func socket path sexp) :line line)]
        (if (result :error)
          (conj results result)
          (recur (.getLineNumber s) (read s false :repl-stream-ended) (conj results result))))
      results)))

(defn eval-string [socket path s eval-func]
  (let [s (stream s)]
    (try
      {:path path
       :eval (eval-stream socket (or path "<repl>") s eval-func)}
      (catch Exception e
        (let [e (repl/root-cause e)]
          {:path path
           :error (pprint-exception e)
           :annotation (.getMessage e)
           :line (.getLineNumber s)})))))

(defn socket-ns [socket target]
  (get @(.data socket "ns") target))

(defn set-socket-ns [socket target ns]
  (swap! (.data socket "ns") assoc target ns))
