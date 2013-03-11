(ns catnip.repl
  (:use-macros [redlobster.macros :only [waitp let-realised]])
  (:require [catnip.dom :as dom]
            [catnip.commands :as cmd :refer [defcommand]]
            [catnip.editor :as editor]
            [catnip.component :as c]
            [catnip.socket :as socket]
            [catnip.pprint :refer [pprint]]
            [redlobster.events :as e]
            [clojure.string :as string]))

(declare repl-print)

(defn- update-namespace [repl ns]
  (when ns
    (dom/text! (:prompt repl) (name ns))))

(defrecord REPL [input display prompt state]
  c/IComponent

  (-init [repl]
    (e/on input :keydown (cmd/event-handler repl :repl :global))
    (socket/on-message
     (fn [x]
       (when-let [ns (:ns x)] (update-namespace repl ns))
       (when-let [out (:out x)] (repl-print :out out)))))

  (-destroy [repl]
    (e/remove-all-listeners input nil)))

(def ^:private current-repl (atom nil))

(defn create-repl [input display prompt]
  (let [repl (REPL. input display prompt
                    (atom {:history-pos 0
                           :history ()}))]
    (c/-init repl)
    (reset! current-repl repl)))

(defn active? [repl]
  (= (dom/active-element) (:input repl)))

(defn focus [repl]
  (.focus (:input repl)))

(defn- push-history! [repl value]
  (swap! (:state repl)
         (fn [state]
           (assoc state
             :history-pos 0
             :history (cons value (:list state))))))

(defn- linkify [msg]
  ;; FIXME: implement
  msg)

(defn- print-node
  ([node]
     (print-node @current-repl node))
  ([repl node]
     (let [node (dom/html node)]
       (dom/append! (:display repl) node)
       (dom/scroll-into-view! node))))

(defn repl-print [type msg & [ns]]
  (print-node [:p {:class (name type)}
               (if (and (= type :code) ns)
                 (list (str ns "» ")
                       (if (string? msg)
                         [:span.clojure msg]
                         (pprint msg)))
                 (if (string? msg)
                   (linkify msg)
                   (pprint msg)))]))

(defn- breakable [t]
  (string/replace t #"([.$/-])" #(str "\u200B" %)))

(defn- elem-length [el]
  (if (and (:file el) (:line el))
    (count (str (:file el) ":" (:line el)))
    (count "(Unknown Source")))

(defn- trace-source [el]
  [:span.source
   (if (and (:file el) (:line el))
     (str (:file el) ":" (:line el))
     "(Unknown Source)")])

(defn- trace-method [el]
  [:span.method
   (if (:java el)
     (breakable (str (:class el) "." (:method el)))
     (let [fn (str (:fn el) (if (:anon-fn el)
                              " [fn]" ""))]
       (breakable (str (:ns el) "/" fn))))])

(defn- trace-elem [trace-width el]
  (let [source-pad (- trace-width (elem-length el))
        spaces (apply str (repeat source-pad " "))
        a #(if (:local el)
             [:a {:href (:local el) :data-line (:line el)} %]
             %)]
    [:p.trace-elem {:style (str "margin-left: "
                                (inc (/ trace-width 2)) "em; "
                                "text-indent: -"
                                (inc (/ trace-width 2)) "em; ")}
     spaces
     (a (list (trace-source el) " " (trace-method el)))]))

(defn- exception-node [e pos-in-file]
  (if (and (:cause e)
           (re-matches #".*clojure\.lang\.Compiler\$CompilerException.*"
                       (:class e)))
    (exception-node (:cause e) pos-in-file)

    (let [trace-width (apply max (map elem-length (:trace-elems e)))
          trace-elem (partial trace-elem trace-width)
          errline (list [:span.class (:class e)] ": "
                        [:span.message (:message e)])
          errline (if pos-in-file
                    [:a {:href (:path pos-in-file)
                         :data-line (:row pos-in-file)}
                     errline]
                    errline)
          node [:div.exception
                [:p.message errline]
                (map trace-elem (:trace-elems e))]]
      (if (:cause e)
        (conj node (list [:p.caused-by "Caused by:"]
                         (exception-node (:cause e))))
        node))))

(defn repl-print-exception [error & [pos-in-file]]
  (print-node (exception-node error pos-in-file)))

(defn- print-eval [ann msg & [skip-first]]
  ;; FIXME: implement (correct-lines msg)
  (let [results (:eval msg)
        anns (:eval ann)
        results (map (partial apply merge)
                     (partition 2 (interleave anns results)))
        print-form
        (fn [result skip-input]
          (when-not skip-input
            (repl-print :code (-> result :code :form) (-> result :code :ns)))
          (when-let [out (:out result)] (repl-print :out out))
          (if-let [error (:error result)]
            (repl-print-exception error)
            (repl-print :result (:result result))))]
    (print-form (first results) skip-first)
    (doseq [result (rest results)] (print-form result false))))

(defn repl-eval [code]
  (let-realised [ann (socket/send {:annotate code :target :node})]
    (let [forms (:eval @ann)
          form1 (:code (first forms))]
      ;; Print the first form while the code evaluates
      (repl-print :code (:form form1) (:ns form1))
      (let-realised [result (socket/send {:eval code :target :node})]
        (print-eval @ann @result true)))))

(defcommand "toggle-repl"
  (fn [_]
    (if-not (active? @current-repl)
      (focus @current-repl)
      (editor/focus))))

(defcommand "repl-eval"
  (fn [repl]
    (let [value (dom/value (:input repl))]
      (dom/value! (:input repl) "")
      (push-history! repl value)
      (repl-eval value))))
