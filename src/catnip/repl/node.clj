;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this file,
;; You can obtain one at http://mozilla.org/MPL/2.0/.

(ns catnip.repl.node
  (:require [cljs.repl.node :as node]
            [cljs.repl :as repl]
            [cljs.analyzer :as ana]
            [catnip.repl.util :refer [ppr pprint-exception]]
            [catnip.annotate :refer [annotate-form]]
            [catnip.edn :as edn]
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

(defn make-env [socket]
  (let [repl-env (node/repl-env :output (partial print-to-socket socket))]
    (repl/-setup repl-env)
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
                              (str
                               "#<"
                               (second
                                (re-matches #"^(function\s*\([^)]*\))(?:.|\n)*"
                                            (str form#)))
                               ">")
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

(defn eval-sexp [socket form]
  (let [repl-env (get-env socket)
        code-ns (str (.data socket "node.ns"))
        env {:context :expr :locals {} :ns (.data socket "node.ns")}
        ann-form (repl/evaluate-form repl-env env "<cljs repl>"
                                     (list 'quote form) represent)
        result (repl/evaluate-form repl-env env "<cljs repl>" form represent)]
    (.data socket "node.ns" ana/*cljs-ns*)
    {:code {:ns code-ns :text (ppr form)
            :form (when ann-form (read-string ann-form))}
     :result (when result (read-string result))}))
