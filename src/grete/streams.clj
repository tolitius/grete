(ns grete.streams
  (:require [grete.tools :as t]
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
                                             ValueMapper]))

;; config

(defn to-stream-prop [prop]
  (try
    (->> prop
         t/kebab->screaming-snake
         (Reflector/getStaticField StreamsConfig))
    (catch Exception e
      (let [msg (str "kafka streams does not understand this property name \"" prop "\"")]
        (println "(!)" msg)
        (throw (RuntimeException. msg e))))))

;; TODO: handle StreamsConfig.consumerPrefix/producerPrefix
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

;; directing flow

(defn topic->stream
  ([builder topic]
   (topic->stream builder topic {:key-serde (string-serde)
                                 :value-serde (string-serde)}))
  ([builder topic {:keys [key-serde value-serde]}]
   (.stream builder topic (Consumed/with key-serde
                                         value-serde))))

(defn stream->topic
  ([stream topic]
   (stream->topic stream topic {:key-serde (string-serde)
                                :value-serde (string-serde)}))
  ([stream topic {:keys [key-serde value-serde]}]
   (.to stream topic (Produced/with key-serde
                                    value-serde))))

;; ->fn kafka stream wrappers

(deftype FunValueMapper [fun]
  ValueMapper
  (apply [_ v]
    (fun v)))

(defn map-values [stream fun]
  (.mapValues stream (FunValueMapper. fun)))

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
