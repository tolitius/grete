(ns grete.tools
  (:require [clojure.string :as s]))

(defn s->seq [xs]
  (when (seq xs)
    (s/split xs #",")))

(defn ^String kebab->screaming-snake [k]
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
