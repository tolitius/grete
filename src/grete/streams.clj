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
                                             Named
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

(defn named-as [op f]
  (Named/as (str
              (name op) "." (t/stream-fn->name f))))

(deftype FunValueMapper [fun]
  ValueMapper
  (apply [_ v]
    (fun v)))

(defn map-values
  ([fun]
   (partial map-values fun))
  ([fun stream]
   (.mapValues stream
               (FunValueMapper. fun)
               (named-as :map-values fun))))

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
   (.map stream
         (FunKeyValueMapper. fun)
         (named-as :map-kv fun))))

(deftype FunPredicate [fun]
  Predicate
  (test [_ k v]
    (boolean (fun k v))))

(defn filter-kv
  ([fun]
   (partial filter-kv fun))
  ([fun stream]
   (.filter stream
            (FunPredicate. fun)
            (named-as :filter fun))))

(deftype FunValueJoiner [fun]
  ValueJoiner
  (apply [_ left right]
    (fun left right)))

(defn left-join
  ([fun]
   (partial left-join fun))
  ([fun stream table]
   (.leftJoin stream
              table
              (FunValueJoiner. fun)))) ;; KStream.leftJoin(KTable ..) does not have a Named arg ¯\_(ツ)_/¯

(deftype FunForeachAction [fun]
  ForeachAction
  (apply [_ k v]
    (fun k v)
    nil))

(defn for-each
  "you would use foreach to cause side effects based on the input data (similar to peek)
   and then stop further processing of the input data
   (unlike peek, which is not a terminal operation)"
  ([fun]
   (partial for-each fun))
  ([fun stream]
   (.foreach stream
             (FunForeachAction. fun)
             (named-as :for-each fun))))

(defn peek
  "performs a stateless action on each record, and returns an unchanged stream. (details)
   you would use peek to cause side effects based on the input data (similar to foreach)
   and continue processing the input data (unlike foreach, which is a terminal operation)
   peek returns the input stream as-is; if you need to modify the input stream, use map or mapValues instead
   peek is helpful for use cases such as logging or tracking metrics or for debugging and troubleshooting"
  ([fun]
   (partial peek fun))
  ([fun stream]
   (.peek stream
          (FunForeachAction. fun)
          (named-as :peek fun))))


;; topology plumbing

(defn make-streams [config builder]
  (let [topology (.build builder)]
    {:topology topology
     :streams (KafkaStreams. topology
                             (to-stream-config config))}))

(defn describe-topology [streams]
  (-> streams :topology .describe))

(defn start-streams [streams]
  (.start (-> streams :streams))
  streams)

(defn cleanup-streams [streams]
  (.cleanUp (-> streams :streams))
  streams)

(defn stop-streams [streams]
  (.close (-> streams :streams))
  streams)

(defn stream-on! [config make-topology]
  (let [builder (stream-builder)
        _       (make-topology builder)        ;; custom fn, can't rely on it returning a builder
        streams (make-streams config builder)]
    (start-streams streams)
    streams))                                  ;; returns {:streams .. :topology ..}


;; helpers: thinking phase, subject to change

(defn transform
  "for single topic to topic streams
   takes one or more kafka stream functions: map-kv, map-values, filter-kv, etc.
   applies them on a stream from one topic to another

   (transform builder
    [{:from \"foo-topic\" :to \"bar-topc\" :via (k/map-kv some-fn-that-takes-k-and-v)}
     {:from \"baz-topic\" :to \"moo-topc\" :via [(k/map-values some-fn-that-takes-value)
                                                 (k/filter-kv some-fn-that-takes-k-and-v-and-returns-boolean)]}
     {:from \"zoo-topic\"                  :via (k/for-each some-fn-that-takes-k-and-v)}])"
  [builder streams]
  (mapv (fn [{:keys [from to via]
              :or {via identity}}]
          (let [funs (-> via t/to-coll reverse)
                stream (->> (topic->stream builder from)
                            ((apply comp funs)))]
            (when to
              (stream->topic stream to))))
        streams))
