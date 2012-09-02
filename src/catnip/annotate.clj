(ns catnip.annotate
  (:use [clojure.test]
        [clj-info.doc2map :only [get-docs-map]]))

(defn- re? [form]
  (= java.util.regex.Pattern (.getClass form)))

(defn- var-obj? [form]
  (= clojure.lang.Var (.getClass form)))

(defn- fn-obj? [form]
  (.isAssignableFrom clojure.lang.IFn (.getClass form)))

(defn- describe-obj [form]
  {:type :object :value
   {:name (str form)
    :class (.getName (.getClass form))}})

(with-test
  (defn- describe-fn-obj [form]
    (let [class-name (.getName (.getClass form))
          real-name (clojure.string/replace
                     (clojure.string/replace class-name #"\$" "/")
                     #"_" "-")]
      (if (re-find #"\$.*\$" class-name)
        (describe-obj form)
        {:type :function :name real-name
         :value (get-docs-map (symbol real-name))})))
  (is (= {:type :function :name "clojure.core/map"}
         (dissoc (describe-fn-obj map) :value)))
  (is (= {:type :function :name "clojure.core/merge-with"}
         (dissoc (describe-fn-obj merge-with) :value))))

(with-test
  (defn annotate-form
    ([ns form]
       (let [note-seq #(map (partial annotate-form ns) %)
             note-map #(map (fn [[key value]]
                              {:key (annotate-form ns key)
                               :value (annotate-form ns value)})
                            %)
             resolved (try (ns-resolve ns form)
                           (catch Exception e nil))
             metadata (meta resolved)
             value (when (var? resolved) (var-get resolved))]
         (cond
          (special-symbol? form) {:type :special-form
                                  :value (get-docs-map form)}
          (:macro metadata) {:type :macro :name (name form)
                             :value metadata}
          (fn? value) {:type :function :name (name form)
                       :value metadata}
          (nil? form) {:type :symbol :value "nil"}
          (true? form) {:type :symbol :value "true"}
          (false? form) {:type :symbol :value "false"}
          (number? form) {:type :number :value form :name (str form)}
          (string? form) {:type :string :value form}
          (re? form) {:type :re :value (.pattern form)}
          (symbol? form) {:type :symbol :value (name form)
                          :namespace (namespace form)}
          (keyword? form) {:type :keyword :value (name form)
                           :namespace (namespace form)}
          (var-obj? form) {:type :var :value (str form)}
          (list? form) {:type :list :value (note-seq form)}
          (vector? form) {:type :vector :value (note-seq form)}
          (set? form) {:type :set :value (note-seq form)}
          (map? form) {:type :map :value (note-map form)}
          (seq? form) (annotate-form ns (apply list form))
          (fn-obj? form) (describe-fn-obj form)
          :else (describe-obj form)))))

  (is (= {:type :number :value 1337 :name "1337"} (annotate-form *ns* 1337)))
  (is (= {:type :string :value "1337"} (annotate-form *ns* "1337")))
  (is (= {:type :symbol :value "wibble" :namespace nil}
         (annotate-form *ns* 'wibble)))
  (is (= {:type :keyword :value "wibble" :namespace nil}
         (annotate-form *ns* :wibble)))
  (is (= {:type :list :value '({:type :string :value "foo"}
                               {:type :re :value "bar"})}
         (annotate-form *ns* '("foo" #"bar"))))
  (is (= {:type :vector :value '({:type :string :value "foo"}
                                 {:type :re :value "bar"})}
         (annotate-form *ns* ["foo" #"bar"])))
  (is (= {:type :set :value '({:type :string :value "foo"})}
         (annotate-form *ns* #{"foo"})))
  (is (= {:type :map :value '({:key {:type :keyword :value "foo"
                                     :namespace nil}
                               :value {:type :string :value "bar"}})}
         (annotate-form *ns* {:foo "bar"})))
  (is (= {:type :list :value '({:type :symbol :value "nil"}
                               {:type :symbol :value "true"}
                               {:type :symbol :value "false"})}
         (annotate-form *ns* '(nil true false))))
  (is (= :function (:type (annotate-form *ns* 'map))))
  (is (= :function (:type (annotate-form (create-ns 'catnip.paths) 'dotfile?))))
  (is (= :macro (:type (annotate-form *ns* 'defn))))
  (is (= :special-form (:type (annotate-form *ns* 'if))))
  (is (= :object (:type (annotate-form *ns* (create-struct :foo))))))
