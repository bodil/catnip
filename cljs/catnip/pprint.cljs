(ns catnip.pprint
  (:require [catnip.dom :as dom]))

(declare pprint)

(defn- span [class text]
  [:span.clojure {:class (name class)} text])

(defn- a [class text doc]
  [:a.clojure {:class (name class)
               :data-doc (JSON/stringify (clj->js doc))}
   text])

(defn- seq-node [class parens forms & [argsub]]
  (let [s (conj (span class nil) (span :lparen (first parens)))
        pprint (if argsub #(pprint % argsub) pprint)
        s (concat s (interpose (span :whitespace " ") (map pprint forms)))]
    (conj (vec s) (span :rparen (second parens)))))

(defn- reader-char [form]
  (when (= 2 (count (:value form)))
    (let [car (-> form :value first)
          type (:type car)
          value (:value car)
          name (:name value)]
      (cond
       (and (= "special-form" type) (#{"quote" "var"} name))
       name

       (and (= "symbol" type) (= "clojure.core" (:namespace car))
            (#{"unquote" "unquote-splicing"} value))
       value

       (and (= "function" type) (= "clojure.core" (-> car :value :ns))
            (= "deref" name))
       name))))

(def ^:private reader-chars
  {"quote" "'"
   "unquote" "~"
   "unquote-splicing" "~@"
   "deref" "@"
   "var" "#'"})

(defn- shortfn [form]
  (let [value (:value form)
        car (first value)]
    (when (and (> (count value) 2)
               (= "special-form" (:type car))
               (= "fn*" (-> car :value :name))
               (= "vector" (:type (second value))))
      (seq-node :list ["#(" ")"] (:value (nth value 2))
                (for [x (:value (second value))] (:value x))))))

(defn- map-node [form]
  (let [s (conj (span :map nil) (span :lparen "{"))
        pairs (for [pair (:value form)]
                (list (pprint (:key pair)) " " (pprint (:value pair))))
        pairs (interpose ", " pairs)
        s (conj s pairs)]
    (conj s (span :rparen "}"))))

(defn- substitute-arg [argsub value]
  (first (keep-indexed #(if (= value %2) %1) argsub)))

(defn pprint [form & [argsub]]
  (case (:type form)
    :list
    (if-let [rc (reader-char form)]
      (span :reader-char (reader-chars rc))
      (or (shortfn form)
          (seq-node :list ["(" ")"] (:value form))))

    :vector
    (seq-node :list ["[" "]"] (:value form))

    :set
    (seq-node :set ["#{" "}"] (:value form))

    :map
    (map-node form)

    :function
    (a :function (:name form) (:value form))

    :macro
    (a :macro (:name form) (:value form))

    :special-form
    (a :special-form (:name form) (:value form))

    :symbol
    (span :symbol
          (let [name (:value form)]
            (if-let [ns (:namespace form)]
              (str ns "/" name)
              (if-let [sub (and argsub (substitute-arg argsub name))]
                (str (inc sub))
                name))))

    :keyword
    (span :keyword
          (let [name (:value form)]
            (if-let [ns (:namespace form)]
              (str ":" ns "/" name)
              (str ":" name))))

    :number
    (span :number (:name form))

    :string
    (span :string (str "\"" (:value form) "\""))

    :re
    (span :re (str "#\"" (:value form) "\""))

    :var
    (span :var (:value form))

    :object
    (span :object (str "#<" (-> form :value :name) ">"))

    (span :error "*ERROR*")))
