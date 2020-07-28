(ns grete.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [grete.gregor :as gregor]
            [grete.scheduler :as sch]))

(defn to-prop [k]
  (-> k name (s/replace #"-" ".")))

(defn to-props
  "ranames keys by converting them to strings and substituting dashes with periods
   only does top level keys"
  [conf]
  (into {}
    (for [[k v] conf]
      [(to-prop k) v])))

;; TODO: waiting on gregor fix for: https://github.com/apache/kafka/pull/5480
#_(defn create-topic [{:keys [name] :as topic}
                    {:keys [zookeeper]}]
  (let [zoo {:connection-string (zookeeper :hosts)}]
    (when-not (gregor/topic-exists? zoo name)
      (gregor/create-topic zoo name topic))))

(defn producer [{:keys [bootstrap-servers] :as conf}]
  (let [serializers (-> (select-keys conf
                                     [:key-serializer :value-serializer])
                        to-props)]
    (gregor/producer bootstrap-servers
                     serializers)))

(defn send!
  "dummy gregor send wrap to:
    1. give it a '!'
    2. avoid requiring another ns to 'send'"
  ([producer topic msg]
   (gregor/send producer topic msg))
  ([producer topic key msg]
   (gregor/send producer topic key msg)))

(defn close [producer]
  (gregor/close producer))

(defn- edn-to-consumer [{:keys [bootstrap-servers
                                group-id
                                topics] :as conf}]
  [bootstrap-servers
   group-id
   topics
   (to-props (dissoc conf :topics))])

;; consuming..

(defn consumer-records->maps [cs]
  (-> (map gregor/consumer-record->map cs)
      seq))

(defn poll
  "fetches sequetially from the last consumed offset
   return 'org.apache.kafka.clients.consumer.ConsumerRecords' currently available to the consumer (via a single poll)
   if a 'timeout' param is 0, returns immediately with any records that are available now."
  ([consumer] (poll consumer 100))
  ([consumer timeout]
   (.poll consumer timeout)))

(defn consumer [conf]
  (log/info "consumer config:" (edn-to-consumer conf))
  (->> (edn-to-consumer conf)
       (apply gregor/consumer)))

(defn consume
  "the 'process' function will take 'org.apache.kafka.clients.consumer.ConsumerRecords'
   which can be turns to a seq of maps with 'consumer-records->maps'"
  [consumer process running? ms n]
  (log/info "starting" (inc n) "consumer")
  (while @running?
    (try
      (let [consumer-records (poll consumer ms)]
        (when consumer-records
          (process consumer consumer-records)
          (gregor/commit-offsets! consumer)))
      (catch Throwable t
        (log/error "kafka: could not consume a message" t))))
  (gregor/close consumer))

(defn run-consumers [process {:keys [threads poll-ms] :as conf}]
  (let [running? (atom true)
        pool (sch/new-executor "kafka consumers" (if (number? threads)
                                                   threads
                                                   42))]
    (dotimes [t threads]
      (let [c (consumer (dissoc conf :threads :poll-ms))]
        (log/info "subscribing to:" (gregor/subscription c))
        (.submit pool #(consume c process running? poll-ms t))))
    (log/info "started" threads "consumers ->" conf)
    {:pool pool :running? running?}))

(defn stop-consumers [{:keys [pool running?]}]
  (reset! running? false)
  (.shutdown pool))

(defn offsets [c]
  (for [tp (gregor/assignment c)]
    (let [p (.partition tp)
          t (.topic tp)]
      {:topic t :partition p :offset (gregor/committed c t p)})))

(defn reset-offsets [c topic pnum]
  (let [offsets (reduce (fn [ofs p]
                          (conj ofs {:topic topic
                                     :partition p
                                     :offset 0})) [] (range pnum))]
    (gregor/commit-offsets! c offsets)))
