;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl.jvm
  (:require [catnip.complete :as complete]
            [catnip.repl.util :refer [ppr pprint-exception map-if-key]])
  (:use [clojure.test]
        [clj-info.doc2map :only [get-docs-map]]
        [catnip.annotate :only [annotate-form]]))

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
