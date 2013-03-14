;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl.node
  (:require [cljs.repl.node :as node]
            [cljs.repl :as repl]
            [cljs.analyzer :as ana]
            [catnip.repl.util :refer [ppr pprint-exception]]
            [catnip.edn :as edn]
            [catnip.repl.util :refer [socket-ns set-socket-ns]]
            [clojure.string :as s]))

(defn print-to-socket [socket s]
  (when-not (.data socket "node.stdout")
    (.data socket "node.stdout" (atom "")))
  (let [buffer (.data socket "node.stdout")]
    (swap! buffer #(str % s))
    (let [break (.indexOf @buffer "\n")]
      (when-not (neg? break)
        (.send socket (edn/to-edn {:out (subs @buffer 0 break) :target :node}))
        (swap! buffer #(subs % (inc break)))))))

(defn send-warning [socket s]
  (when (pos? (count (s/trim s)))
    (.send socket (edn/to-edn {:warn s :target :node}))))

(defn make-env [socket]
  (let [repl-env (node/repl-env :output (partial print-to-socket socket))]
    (repl/-setup repl-env)
    ;; FIXME: (repl/analyze-source "path-to-cljs-files")
    (repl/analyze-source "cljs")
    repl-env))

(defn get-env [socket]
  (when-not (.data socket "node")
    (.data socket "node" (make-env socket)))
  (.data socket "node"))

(defn cleanup [socket]
  (repl/-tear-down (.data socket "node")))

(defn- represent [x]
  `(cljs.core/pr-str
    (let [note#
          (fn note# [form#]
            (let [note-seq# #(map note# %)
                  note-map# #(map (fn [[key# value#]]
                                    {:key (note# key#)
                                     :value (note# value#)}) %)
                  note-fn# (fn [form#]
                             {:type :function
                              :name
                              (str "#<" (second
                                         (re-matches #"^(function[^(]*\([^)]*\))(?:.|\n)*"
                                                     (str form#))) ">")
                              :value (str form#)})]
              (cond
               (nil? form#) {:type :symbol :value "nil"}
               (true? form#) {:type :symbol :value "true"}
               (false? form#) {:type :symbol :value "false"}
               (number? form#) {:type :number :value form# :name (str form#)}
               (string? form#) {:type :string :value form#}
               (instance? js/RegExp form#) {:type :re :value (.-source form#)}
               (symbol? form#) {:type :symbol :value (name form#)
                                :namespace (namespace form#)}
               (keyword? form#) {:type :keyword :value (name form#)
                                 :namespace (namespace form#)}
               (list? form#) {:type :list :value (note-seq# form#)}
               (vector? form#) {:type :vector :value (note-seq# form#)}
               (set? form#) {:type :set :value (note-seq# form#)}
               (map? form#) {:type :map :value (note-map# form#)}
               (seq? form#) (note# (apply list form#))
               (fn? form#) (note-fn# form#)
               :else {:type :object :name (cljs.core/pr-str form#)})))]
      (note# ~x))))

(defn eval-form [socket repl-env env file form represent]
  (binding [ana/*cljs-ns* (socket-ns socket :node)
            ana/*cljs-warn-on-undeclared* true
            *out* (java.io.StringWriter.)]
    (let [result
          (repl/evaluate-form repl-env
                              (assoc env :ns (ana/get-namespace ana/*cljs-ns*))
                              file form represent)]
      (when (and (seq? form) (= 'ns (first form)))
        ;; FIXME: why is this necessary?!??!!!11
        (repl/evaluate-form repl-env (assoc env :ns (ana/get-namespace ana/*cljs-ns*)) file
                            (list 'js* (str "function(){if(!goog.isProvided_('"
                                            (second form) "'))goog.provide('"
                                            (second form) "')}()")) represent))
      (set-socket-ns socket :node ana/*cljs-ns*)
      (if (count (str *out*)) (send-warning socket (str *out*)))
      result)))

(defn eval-sexp [socket path form]
  (let [repl-env (get-env socket)
        env {:context :expr :locals {}}
        result (eval-form socket repl-env env path form represent)]
    {:result (when result (read-string result))}))

(defn annotate-sexp [socket path form]
  (let [repl-env (get-env socket)
        env {:context :expr :locals {}}
        code-ns (str (socket-ns socket :node))
        ann-form (eval-form socket repl-env env path (list 'quote form) represent)]
    {:code {:ns code-ns :text (ppr form)
            :form (when ann-form (read-string ann-form))}}))
