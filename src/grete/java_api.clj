(ns grete.java-api
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [keywordize-keys]]
            [grete.tools :as t]
            [grete.core :as g])
  (:import [org.apache.kafka.clients.consumer KafkaConsumer
                                              ConsumerRecords]))

(gen-class
  :name fn42.Grete
  :methods [^{:static true} [startConsumers [java.util.function.BiConsumer
                                             java.util.Map] java.util.Map]
            ^{:static true} [stopConsumers [java.util.Map] void]
            ^{:static true} [resetOffsets [java.util.Map
                                           String
                                           Long] void]
            ^{:static true} [toMap [java.util.Map] java.util.Map]])

(defn -startConsumers [consume config]
  (let [f (fn [^KafkaConsumer consumer
               ^ConsumerRecords records]
            (.accept consume consumer records))
        edn-config (-> (t/fmk config keyword)
                       (update :topics t/s->seq)
                       (update :threads t/parse-long)
                       (update :poll-ms t/parse-long))]
    (log/info "starting consumers with:" edn-config)
    (g/run-consumers f edn-config)))

(defn -stopConsumers [consumers]
  (g/stop-consumers consumers))

(defn -resetOffsets [config topic pnum]
  (let [edn-conf (-> (t/fmk config keyword)
                     (dissoc :topics :threads :poll-ms))
        consumer (g/consumer edn-conf)]
    (g/reset-offsets consumer topic pnum)))

(defn -toMap [m]
  (-> (t/fmk m name)
      (java.util.HashMap.)))
