(ns grete.tools
  (:require [clojure.string :as s]))

(defn parse-long [n]
  (when n
    (try (Long/valueOf n)
         (catch Exception e))))

(defn s->seq [xs]
  (when (seq xs)
    (s/split xs #",")))

(defn kebab->screaming-snake [k]
  (-> k
      name
      (s/replace #"-" "_")
      s/upper-case))

(defn fmv
  "apply f to each value v of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn fmk
  "apply f to each key k of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [(f k) v])))

