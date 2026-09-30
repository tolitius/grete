(ns grete.core-test
  (:require [clojure.test :refer [deftest is]]
            [grete.core :as core]
            [grete.gregor :as gregor]))

(deftest on-error-receives-consumer-context
  (let [running? (atom true)
        consumer (Object.)
        error    (ex-info "failure" {})
        received (atom nil)]
    (with-redefs [core/poll    (fn [_ _] (throw error))
                  gregor/close (fn [_] (reset! running? false))]
      (core/consume consumer
                    (fn [& _] nil)
                    running?
                    0
                    0
                    {:on-error (fn [context]
                                 (reset! received context)
                                 (reset! running? false))}))
    (is (= {:consumer        consumer
            :consumer-number 0
            :phase           :poll
            :error           error}
           @received))))

(deftest commit-handler-receives-process-result
  (let [running?     (atom true)
        consumer     (Object.)
        commit-error (ex-info "commit failure" {})
        received     (atom nil)]
    (with-redefs [core/poll              (fn [_ _] (Object.))
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _] (throw commit-error))]
      (core/consume consumer
                    (fn [& _] :processed)
                    running?
                    0
                    7
                    {:on-commit-error (fn [context]
                                        (reset! received context)
                                        (reset! running? false))}))
    (is (= {:consumer        consumer
            :consumer-number 7
            :phase           :commit
            :result          :processed
            :error           commit-error}
           @received))))

(deftest poll-handler-takes-precedence
  (let [running? (atom true)
        received (atom [])]
    (with-redefs [core/poll    (fn [_ _] (throw (ex-info "poll failure" {})))
                  gregor/close (fn [_] nil)]
      (core/consume (Object.)
                    (fn [& _] (swap! received conj :process))
                    running?
                    0
                    0
                    {:on-error      (fn [_] (swap! received conj :generic))
                     :on-poll-error (fn [context]
                                      (swap! received conj (:phase context))
                                      (reset! running? false))}))
    (is (= [:poll] @received))))

(deftest process-handler-takes-precedence
  (let [running? (atom true)
        received (atom [])]
    (with-redefs [core/poll              (fn [_ _] (Object.))
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _] (swap! received conj :commit))]
      (core/consume (Object.)
                    (fn [& _] (throw (ex-info "process failure" {})))
                    running?
                    0
                    0
                    {:on-error         (fn [_] (swap! received conj :generic))
                     :on-process-error (fn [context]
                                         (swap! received conj (:phase context))
                                         (reset! running? false))}))
    (is (= [:process] @received))))

(deftest commit-handler-takes-precedence
  (let [running? (atom true)
        received (atom [])]
    (with-redefs [core/poll              (fn [_ _] (Object.))
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _] (throw (ex-info "commit failure" {})))]
      (core/consume (Object.)
                    (fn [& _] :processed)
                    running?
                    0
                    0
                    {:on-error        (fn [_] (swap! received conj :generic))
                     :on-commit-error (fn [context]
                                        (swap! received conj (:phase context))
                                        (reset! running? false))}))
    (is (= [:commit] @received))))

(deftest process-failure-does-not-commit
  (let [running?   (atom true)
        committed? (atom false)]
    (with-redefs [core/poll              (fn [_ _] (Object.))
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _] (reset! committed? true))]
      (core/consume (Object.)
                    (fn [& _] (throw (ex-info "process failure" {})))
                    running?
                    0
                    0
                    {:on-process-error (fn [_] (reset! running? false))}))
    (is (false? @committed?))))

(deftest nil-poll-result-skips-process-and-commit
  (let [running? (atom true)
        polls    (atom 0)
        touched  (atom [])]
    (with-redefs [core/poll              (fn [_ _]
                                           (when (< 1 (swap! polls inc))
                                             (reset! running? false))
                                           nil)
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _] (swap! touched conj :commit))]
      (core/consume (Object.)
                    (fn [& _] (swap! touched conj :process))
                    running?
                    0
                    0
                    {}))
    (is (= [] @touched))))

(deftest nil-process-result-still-commits
  (let [running?   (atom true)
        committed? (atom false)]
    (with-redefs [core/poll              (fn [_ _] (Object.))
                  gregor/close           (fn [_] nil)
                  gregor/commit-offsets! (fn [& _]
                                           (reset! committed? true)
                                           (reset! running? false))]
      (core/consume (Object.)
                    (fn [& _] nil)
                    running?
                    0
                    0
                    {}))
    (is (true? @committed?))))

(deftest nil-on-error-uses-default-handler
  (let [running? (atom true)
        received (atom nil)
        error    (ex-info "failure" {})]
    (with-redefs [core/poll             (fn [_ _] (throw error))
                  core/default-on-error (fn [context]
                                          (reset! received context)
                                          (reset! running? false))
                  gregor/close          (fn [_] nil)]
      (core/consume (Object.)
                    (fn [& _] nil)
                    running?
                    0
                    7
                    {:on-error nil}))
    (is (= 7 (:consumer-number @received)))
    (is (= :poll (:phase @received)))
    (is (= error (:error @received)))))

(deftest on-error-failure-does-not-prevent-consumer-close
  (let [running? (atom true)
        closed?  (atom false)]
    (with-redefs [core/poll    (fn [_ _] (throw (ex-info "failure" {})))
                  gregor/close (fn [_] (reset! closed? true))]
      (core/consume (Object.)
                    (fn [& _] nil)
                    running?
                    0
                    0
                    {:on-error (fn [_]
                                 (reset! running? false)
                                 (throw (ex-info "handler failure" {})))}))
    (is (true? @closed?))))
