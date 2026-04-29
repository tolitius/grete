(ns grete.java-api
  (:require [clojure.tools.logging :as log]
            [grete.tools :as t]
            [grete.core :as g])
  (:import [java.util HashMap Map]
           [java.util.function BiConsumer]
           [org.apache.kafka.clients.consumer KafkaConsumer ConsumerRecords]))

(gen-class
  :name tolitius.Grete
  :methods [^{:static true} [startConsumers [BiConsumer Map] Map]
            ^{:static true} [stopConsumers [Map] void]
            ^{:static true} [resetOffsets [Map String Long] void]
            ^{:static true} [toMap [Map] Map]])

(defn -startConsumers [^BiConsumer consume ^Map config]
  (let [f (fn [^KafkaConsumer consumer
               ^ConsumerRecords records]
            (.accept consume consumer records))
        edn-config (-> (update-keys config keyword)
                       (update :topics t/s->seq)
                       (update :threads parse-long)
                       (update :poll-ms parse-long))]
    (log/info "starting consumers with:" edn-config)
    (g/run-consumers f edn-config)))

(defn -stopConsumers [^Map consumers]
  (g/stop-consumers consumers))

(defn -resetOffsets [^Map config ^String topic ^Long pnum]
  (let [edn-conf (-> (update-keys config keyword)
                     (dissoc :topics :threads :poll-ms))
        consumer (g/consumer edn-conf)]
    (g/reset-offsets consumer topic pnum)))

(defn -toMap [^Map m]
  (HashMap. ^Map (update-keys m name)))
