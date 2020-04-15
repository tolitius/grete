(def +version+ "0.1.1")

(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure             "1.10.1"]
                  [org.clojure/tools.logging       "1.0.0"]
                  [tolitius/gregor                 "1.0.1"   :exclusions [org.apache.kafka/kafka_2.12]]
                  [org.apache.kafka/kafka_2.12     "2.4.1"]

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
  (set-env! :resource-paths #(conj % "dev-resources"))
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
  pom {:project     'tolitius/grete
       :version     +version+
       :description "where all configuration properties converge"
       :url         "https://github.com/tolitius/grete"
       :scm         {:url "https://github.com/tolitius/grete"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
