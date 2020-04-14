(ns grete.scheduler
  (:require [clojure.tools.logging :as log]
            [grete.tools :as t])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent ThreadFactory Executors TimeUnit ScheduledExecutorService ExecutorService]
           [java.time Instant ZoneId ZonedDateTime]
           [java.time.format DateTimeFormatter]))

(deftype GreteThreadFactory [name ^AtomicInteger thread-counter]
  ThreadFactory

  (newThread [_ r]
    (doto
      (Thread. r)
      (.setName (format "%s-%d" name (.getAndIncrement thread-counter)))
      (.setDaemon true)
      (.setUncaughtExceptionHandler
        (reify Thread$UncaughtExceptionHandler
          (uncaughtException [_ thread ex]
            (log/error (format "error in thread id: %s name: %s" (.getId thread) (.getName thread)) ex)))))))

(defn new-executor [name num-threads]
   (Executors/newFixedThreadPool num-threads
                                 (GreteThreadFactory. name
                                                        (AtomicInteger. 0))))

(defn run-fun [name fun threads]
  (let [threads (or (t/parse-long (str threads)) 1)
        pool (new-executor name threads)
        running? (atom true)
        ^Runnable spinner #(while @running?
                             (try (fun)
                                  (catch Throwable t
                                    (log/error t))))]
    (dotimes [_ threads]
      (.submit pool spinner))

    {:pool pool :running? running?}))

(defn stop [f]
  (.cancel f true))

(defn every [interval fun & {:keys [initial-delay time-unit]
                             :or {initial-delay 0
                                  time-unit TimeUnit/MILLISECONDS}}]
  (let [f #(try (fun) (catch Exception e (log/error (.printStackTrace e System/out))))]
    (.scheduleAtFixedRate (Executors/newScheduledThreadPool 1)
                          f initial-delay interval time-unit)))

(defn do-times [n f]
  (future
    (dotimes [_ n]
      (try (f)
        (catch Exception e
          (log/error (.printStackTrace e System/out))))
      (Thread/sleep 1000))))
