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

(defn kebab->dotted [k]
  (-> k
      name
      (s/replace #"-" ".")))

(defn to-coll [x]
  (if (coll? x)
    x
    [x]))

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

(defn random-string []
  (apply str
         (repeatedly 8 #(rand-nth "abcdefghijklmnopqrstuvwxyz0123456789"))))

(defn stream-fn->name [f]
  (-> f
      str
      (s/replace #"@.*$" "")
      (s/replace #"^.*\$" "")
      (s/replace "__GT_" "-")
      (s/replace "_LT__" "-")
      (s/replace "_" "-")
      (str ".00" (random-string))))

(defn cloak-secrets [{:keys [ssl-keystore-certificate-chain
                             ssl-keystore-key] :as config}]
  (if (or ssl-keystore-key
          ssl-keystore-certificate-chain)
    (-> config
        (assoc :ssl-keystore-certificate-chain "****")
        (assoc :ssl-keystore-key "****"))
    config))
