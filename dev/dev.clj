(ns dev
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as s]
            [grete.streams :as k]
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
                                             ValueMapper]))

;; kafka streams

(def config {:application-id-config "exploring-canis-major-dwarf-galaxy"
             :client-id-config "galactic-disk"
             :bootstrap-servers-config "localhost:9092"
             :default-key-serde-class-config (-> (k/string-serde) .getClass .getName)
             :default-value-serde-class-config (-> (k/string-serde) .getClass .getName)})

(defn find-stars [galaxy]
  (-> galaxy
      (json/read-value json/keyword-keys-object-mapper)
      :stars
      (json/write-value-as-string json/keyword-keys-object-mapper)))


(defn make-streams [builder]
  (-> (k/topic->stream builder "canis-systems")
      (k/map-values find-stars)
      (k/stream->topic "stars-with-planets")))

(defn start []
  (let [topology (k/stream-on! config make-streams)]
    (Thread/sleep 1000)
    (k/stop-topology topology)))
