(def +version+ "0.1.0-SNAPSHOT")

(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure             "1.10.1"]
                  [org.clojure/tools.logging       "0.5.0"]
                  [io.weft/gregor                  "1.0.0"   :exclusions [org.apache.kafka/kafka_2.12]]
                  [org.apache.kafka/kafka_2.12     "2.3.1"]
                  [org.clojure/core.async          "0.4.500"]

                  ;; boot clj
                  [boot/core                "2.7.1"           :scope "provided"]
                  [adzerk/bootlaces         "0.1.13"          :scope "test"]
                  [adzerk/boot-test         "1.0.6"           :scope "test"]
                  [tolitius/boot-check      "0.1.1"           :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[tolitius.boot-check :as check]
         '[adzerk.boot-test :as bt])

(bootlaces! +version+)

(deftask dev []
  (repl))

(deftask test []
  (bt/test))

(deftask check-sources []
  (comp
    (check/with-bikeshed)
    (check/with-eastwood)
    (check/with-yagni)
    (check/with-kibit)))

(task-options!
  push {:ensure-branch nil}
  aot {:all true}
  pom {:project     'fn42/grete
       :version     +version+
       :description "where all configuration properties converge"
       :url         "https://github.com/tolitius/grete"
       :scm         {:url "https://github.com/tolitius/grete"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
