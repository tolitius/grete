(ns grete.streams
  (:require [clojure.string :as s]
            [grete.tools :as t]
            [jsonista.core :as json])
  (:import [clojure.lang Reflector]
           [java.util Properties]
           [org.apache.kafka.common.serialization Serde
                                                  Serdes]
           [org.apache.kafka.streams KafkaStreams
                                     KeyValue
                                     StreamsBuilder
                                     StreamsConfig]
           [org.apache.kafka.streams.kstream Consumed
                                             KStream
                                             Produced
                                             Predicate
                                             ValueMapper
                                             ValueJoiner
                                             KeyValueMapper
                                             ForeachAction]))

;; config


; {:application-id-config "ecaf-snoop"
;  :client-id-config "augment-plan-snooper"
;  :bootstrap-servers-config "localhost:9092"
;  :default-key-serde-class-config (-> (k/string-serde) .getClass .getName)
;  :default-value-serde-class-config (-> (k/string-serde) .getClass .getName)

;  :producer.max-request-size (int 16384255)
;  :consumer.max-request-size (int 16384255)}
(defn to-stream-prop [prop]
  (try
    (if (some #(s/starts-with? (name prop) %) #{StreamsConfig/CONSUMER_PREFIX
                                                StreamsConfig/PRODUCER_PREFIX
                                                StreamsConfig/ADMIN_CLIENT_PREFIX
                                                StreamsConfig/GLOBAL_CONSUMER_PREFIX
                                                StreamsConfig/MAIN_CONSUMER_PREFIX
                                                StreamsConfig/RESTORE_CONSUMER_PREFIX
                                                StreamsConfig/TOPIC_PREFIX})
      (t/kebab->dotted prop)
      (->> prop
           t/kebab->screaming-snake
           (Reflector/getStaticField StreamsConfig)))
    (catch Exception e
      (let [msg (str "kafka streams does not understand this property name \"" prop "\"")]
        (println "(!)" msg)
        (throw (RuntimeException. msg e))))))

(defn to-stream-config [m]
  (let [ps (Properties.)]
    (doseq [[k v] m]
      (.put ps (to-stream-prop k)
            v))
    ps))

;; core

(defn stream-builder []
  (StreamsBuilder.))

;; serializers / deserializers

(defn string-serde []
  (Serdes/String))

(defn long-serde []
  (Serdes/Long))

(defn int-serde []
  (Serdes/Integer))

;; directing flow

(defn topic->stream
  ([builder topic]
   (topic->stream builder topic {}))
  ([builder topic {:keys [key-serde value-serde]
                   :or {key-serde (string-serde)
                        value-serde (string-serde)}}]
   (.stream builder topic (Consumed/with key-serde
                                         value-serde))))

(defn topic->table
  ([builder topic]
   (topic->table builder topic {}))
  ([builder topic {:keys [key-serde value-serde]
                   :or {key-serde (string-serde)
                        value-serde (string-serde)}}]
   (.table builder topic (Consumed/with key-serde
                                        value-serde))))

(defn topic->global-table
  ([builder topic]
   (topic->global-table builder topic {}))
  ([builder topic {:keys [key-serde value-serde]
                   :or {key-serde (string-serde)
                        value-serde (string-serde)}}]
   (.globalTable builder topic (Consumed/with key-serde
                                              value-serde))))

(defn stream->topic
  ([stream topic]
   (stream->topic stream topic {}))
  ([stream topic {:keys [key-serde value-serde]
                   :or {key-serde (string-serde)
                        value-serde (string-serde)}}]
   (.to stream topic (Produced/with key-serde
                                    value-serde))))

;; ->fn kafka stream wrappers

(deftype FunValueMapper [fun]
  ValueMapper
  (apply [_ v]
    (fun v)))

(defn map-values
  ([fun]
   (partial map-values fun))
  ([fun stream]
   (.mapValues stream (FunValueMapper. fun))))

(defn to-kv [[k v]]
  (KeyValue. k v))

(deftype FunKeyValueMapper [fun]
  KeyValueMapper
  (apply [_ k v]
    (-> (fun k v)
        to-kv)))

(defn map-kv
  ([fun]
   (partial map-kv fun))
  ([fun stream]
   (.map stream (FunKeyValueMapper. fun))))

(deftype FunPredicate [fun]
  Predicate
  (test [_ k v]
    (boolean (fun k v))))

(defn filter-kv
  ([fun]
   (partial filter-kv fun))
  ([fun stream]
   (.filter stream (FunPredicate. fun))))

(deftype FunValueJoiner [fun]
  ValueJoiner
  (apply [_ left right]
    (fun left right)))

(defn left-join
  ([fun]
   (partial left-join fun))
  ([fun stream table]
   (.leftJoin stream table (FunValueJoiner. fun))))

(deftype FunForeachAction [fun]
  ForeachAction
  (apply [_ k v]
    (fun k v)
    nil))

(defn peek
  ([fun]
   (partial peek fun))
  ([fun stream]
   (.peek stream (FunForeachAction. fun))))


;; topology plumbing

(defn build-topology [config builder]
  (KafkaStreams. (.build builder)
                 (to-stream-config config)))

(defn start-topology [streams]
  (.start streams)
  streams)

(defn cleanup-topology [streams]
  (.cleanUp streams)
  streams)

(defn stop-topology [streams]
  (.close streams)
  streams)

(defn stream-on! [config make-topology]
  (let [builder (stream-builder)]
    (make-topology builder)
    (->> builder
         (build-topology config)
         cleanup-topology         ;;TODO: conditionilize, does not apply in production
         start-topology)))


;; helpers: thinking phase, subject to change

(defn transform
  "for single topic to topic streams
   takes one or more kafka stream functions: map-kv, map-values, filter-kv, etc.
   applies them on a stream from one topic to another

   (transform builder
    [{:from \"foo-topic\" :to \"bar-topc\" :via (k/map-kv some-fn-that-takes-k-and-v)}
     {:from \"baz-topic\" :to \"moo-topc\" :via [(k/map-values some-fn-that-takes-value)
                                                 (k/filter-kv some-fn-that-takes-k-and-v-and-returns-boolean)]}])"
  [builder streams]
  (mapv (fn [{:keys [from to via]
              :or {via identity}}]
          (let [funs (-> via t/to-coll reverse)
                stream (->> (topic->stream builder from)
                            ((apply comp funs)))]
            (stream->topic stream to)))
        streams))



