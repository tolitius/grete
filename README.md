## grete

is [gregor](https://github.com/tolitius/grete/blob/master/src/grete/gregor.clj#L2)'s sister that adds a threadpool and a scheduler

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Ftolitius%2Fgrete%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/grete/releases)
[![<! clojars](https://img.shields.io/clojars/v/tolitius/grete.svg)](https://clojars.org/tolitius/grete)

... and some Java API<br/>
... and the latest kafka (at the moment of writing)

the idea behind `grete` is to be able to start a farm of kafka consumers that listen to (potentially) multiple topics and apply a simple consuming function.

## spilling the beans

```clojure
$ boot repl

=> (require '[grete.core :as g])
```

it is quite common for the same app to produce and consume,<br/>
hence we'll use one config for producing and consuming:

```clojure
-=> (def config {:kafka
                 {:producer
                  {:bootstrap-servers "1.1.1.1:9092,2.2.2.2:9092,3.3.3.3:9092"}
                  :consumer
                  {:group-id "foobar-consumer-group"
                   :bootstrap-servers "1.1.1.1:9092,2.2.2.2:9092,3.3.3.3:9092"
                   :topics ["foos" "bars" "bazs"]
                   :threads 42
                   :poll-ms 10
                   :auto-offset-reset "earliest"}}})
```

### produce

produce a couple of messages (to `foos` topic):

```clojure
=> (def p (g/producer "foos" (get-in config [:kafka :producer])))

=> (g/send! p "{:answer 42}")
=> (g/send! p "{:answer 42}")

=> (g/close p)
```

### consume

a sample consuming function "`process`":

```clojure
;; the "process" function will takes a batch of 'org.apache.kafka.clients.consumer.ConsumerRecords'
;; which can be turned to a seq of maps with 'consumer-records->maps'"

=> ;; not using "consumer" arg here, but you may
   (defn process [consumer batch]
     (let [batch (g/consumer-records->maps batch)
           bsize (count batch)]
       (when (pos? bsize)
         (println "picked up" bsize "events:" batch))))
```

start a farm of consumers (`42` threads as per config):

```clojure
=> (def consumers (g/run-consumers process (get-in config [:kafka :consumer])))
```

once the "farm" is started you'll see those two messages that were produces above:

```clojure
;;   picked up 2 events: ({:value {:answer 42},
;;                         :key #object[[B 0x65ae581f [B@65ae581f],
;;                         :partition 2,
;;                         :topic foos,
;;                         :offset 1000,
;;                         :timestamp 1586888551200,
;;                         :timestamp-type CreateTime}
;;                       {:value {:answer 42},
;;                         :key #object[[B 0x499b3437 [B@499b3437],
;;                         :partition 2,
;;                         :topic foos,
;;                         :offset 1001,
;;                         :timestamp 1586889147336,
;;                         :timestamp-type CreateTime})
```

values here are strings, but could be byte arrays given bytearray de/serializers.

as with other thread pools, it's a good idea to shut them down once we done working with them:

```clojure
=> (g/stop-consumers consumers)
```


## Java API

consumer props:

```yaml
bootstrap-servers: "1.1.1.1:9092,2.2.2.2:9092,3.3.3.3:9092"
threads: 42
poll-ms: 10
topics: "foos,bars,bazs"
group-id: "foobar-consumer-group"
auto-offset-reset: "earliest"
enable-auto-commit: "false"
heartbeat-interval-ms: "3000"
default-api-timeout-ms: "600000"
session-timeout-ms: "30000"
```

a mesage processing function:

```java
static void process(ConsumerRecords<byte[], byte[]> records) {
   // ...
}
```

a map of consumers:

```java
import tolitius.Grete;

BiConsumer<KafkaConsumer, ConsumerRecords<byte[], byte[]>> consume =
        (consumer, records) -> process(records);

Map consumers = Grete.startConsumers(consume, props);

Grete.stopConsumers(consumers);
```

could be "`process(consumer, records)`" if "KafkaConsumer" is also needed

### several topics at once

In case the same group of consumer threads are listening to multiple topics _and_ the distinction needs to be made, i.e. what messages came from which topics, the records need to be groupped by topic:

```java
static Map<String, List<ConsumerRecord<byte[], byte[]>>> groupByTopic(ConsumerRecords<byte[], byte[]> records) {

    if (records.isEmpty()) {
        log.trace("no new records in kafka, hence there is nothing to transport");
        return null;
    }

    var byTopic = new ConcurrentHashMap<String, List<ConsumerRecord<byte[], byte[]>>>();

    records.forEach(record -> {
        var topic = record.topic();
        var rs = byTopic.getOrDefault(topic, new ArrayList<>());
        rs.add(record);
        byTopic.put(record.topic(), rs);
    });

    return byTopic;
}
```

this is the "`process`" function from a previuos example with a group by topic:

```java
static void process(ConsumerRecords<byte[], byte[]> records) {

    // since consumer may be subscribed to multiple topics the batch might include
    // records of different types / from different topics.
    // group all the the records in the batch by the topic to later pipe it to the proper function
    var byTopic = groupByTopic(records);

    if (byTopic != null) {
        byTopic.forEach((topic, rs) -> {

            // ...
        });
    }
}
```

## License

Copyright © 2020 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
