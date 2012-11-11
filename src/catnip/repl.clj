;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [catnip.complete :as complete]
            [catnip.filesystem :as fs]
            [clj-stacktrace.core :as stacktrace]
            [clojure.repl :as repl])
  (:use [clojure.test]
        [clj-info.doc2map :only [get-docs-map]]
        [catnip.annotate :only [annotate-form]]))

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

(defn eval*
  ([socket sexp]
     (eval* socket sexp {}))
  ([socket sexp bindings]
     (with-bindings
         (assoc bindings #'*ns* (.data socket "ns"))
       (let [result (eval sexp)]
         (.data socket "ns" *ns*)
         result))))

(defn eval-sexp [socket sexp]
  (let [out (java.io.StringWriter.)
        code-ns (str (.data socket "ns"))]
    (try
      (let [result (eval* socket sexp
                          {#'*out* out #'*err* out
                           #'*test-out* out})]
        {:code {:ns code-ns :text (ppr sexp)
              :form (annotate-form (.data socket "ns") sexp)}
       :result (annotate-form (.data socket "ns") result)
       :out (str out)})
      (catch Exception e
        (let [e (repl/root-cause e)
              msg (.getMessage e)
              chop-exc-re #"^java\.[\w.]+Exception: (.*)$"
              errline-re #":(\d+)\)$"]
          {:code {:ns code-ns :text (ppr sexp)
                  :form (annotate-form (.data socket "ns") sexp)}
           :out (str out)
           :error (pprint-exception e)
           :annotation
           (or (if-let [m (re-find chop-exc-re msg)] (second m)) msg)
           :errline
           (if-let [m (re-find errline-re msg)] (Integer. (second m)))})))))

(defn eval-stream [socket s]
  (loop [line (.getLineNumber s)
         sexp (read s false nil)
         results []]
    (if-not (nil? sexp)
      (let [result (assoc (eval-sexp socket sexp) :line line)]
        (if (result :error)
          (conj results result)
          (recur (.getLineNumber s) (read s false nil) (conj results result))))
      results)))

(defn eval-string [socket path s]
  (let [s (stream s)]
    (try
      {:path path
       :eval (eval-stream socket s)}
      (catch Exception e
        (let [e (repl/root-cause e)]
          {:path path
           :error (pprint-exception e)
           :annotation (.getMessage e)
           :line (.getLineNumber s)})))))

(defn resolve-ns [socket ns]
  (if ns (create-ns (symbol ns))
      (if socket (.data socket "ns") (create-ns 'user))))

(defn complete-string [socket s ns]
  (let [ns (resolve-ns socket ns)]
    (complete/completions s ns)))

(defn document-symbol [socket s ns]
  (let [ns (resolve-ns socket ns)]
    (with-bindings {#'*ns* ns}
      (map-if-key (get-docs-map s)
                  :ns ns-name
                  :all-other-fqv #(map str %)
                  :inline str
                  :tag str
                  :test str))))
