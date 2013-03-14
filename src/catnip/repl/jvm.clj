;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl.jvm
  (:require [catnip.complete :as complete]
            [clojure.repl :as repl]
            [catnip.repl.util :refer [ppr pprint-exception map-if-key socket-ns set-socket-ns]])
  (:use [clojure.test]
        [clj-info.doc2map :only [get-docs-map]]
        [catnip.annotate :only [annotate-form]]))

(defn eval*
  ([socket sexp]
     (eval* socket sexp {}))
  ([socket sexp bindings]
     (with-bindings
       (assoc bindings #'*ns* (socket-ns socket :jvm))
       (let [result (eval sexp)]
         (set-socket-ns socket :jvm *ns*)
         result))))

(defn eval-sexp [socket path sexp]
  (let [out (java.io.StringWriter.)
        code-ns (str (socket-ns socket :jvm))]
    (try
      (let [result (eval* socket sexp
                          {#'*out* out #'*err* out
                           #'*test-out* out})]
        {:result (annotate-form (socket-ns socket :jvm) result)
         :out (str out)})
      (catch Exception e
        (let [e (repl/root-cause e)
              msg (.getMessage e)
              chop-exc-re #"^java\.[\w.]+Exception: (.*)$"
              errline-re #":(\d+)\)$"]
          {:code {:ns code-ns :text (ppr sexp)
                  :form (annotate-form (socket-ns socket :jvm) sexp)}
           :out (str out)
           :error (pprint-exception e)
           :annotation
           (or (if-let [m (re-find chop-exc-re msg)] (second m)) msg)
           :errline
           (if-let [m (re-find errline-re msg)] (Integer. (second m)))})))))

(defn annotate-sexp [socket path sexp]
  (let [code-ns (str (socket-ns socket :jvm))]
    {:code {:ns code-ns :text (ppr sexp)
            :form (annotate-form code-ns sexp)}}))

(defn resolve-ns [socket ns]
  (if ns (create-ns (symbol ns))
      (if socket (socket-ns socket :jvm) (create-ns 'user))))

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
