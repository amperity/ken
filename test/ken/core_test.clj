(ns ken.core-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [ken.core :as ken]
    [ken.event :as event]
    [ken.tap :as tap]
    [ken.trace :as trace]
    [manifold.deferred :as d])
  (:import
    (java.util.concurrent
      CompletableFuture
      CompletionStage)))


(defmacro capture-observed
  "Testing macro which captures all events sent to the tap in an `observed`
  atom."
  [& body]
  `(let [~'observed (atom [])]
     (with-redefs [tap/send (fn [event# _#]
                              (swap! ~'observed conj event#)
                              true)]
       ~@body)))


(deftest observe-events
  (testing "basic usage"
    (capture-observed
      (is (true? (ken/observe {::event/label "foo"})))
      (is (= 1 (count @observed)))
      (let [event (first @observed)]
        (is (inst? (::event/time event)))
        (is (= :info (::event/level event)))
        (is (= "foo" (::event/label event)))
        (is (string? (::event/thread event)))
        (is (string? (::trace/trace-id event)))
        (is (string? (::trace/span-id event))))))
  (testing "inside span"
    (capture-observed
      (ken/watch "foo"
        (is (true? (ken/observe
                     {::event/label "bar"
                      ::event/level :warn
                      ::thing 123}))))
      (is (= 2 (count @observed)))
      (let [[event span] @observed]
        (is (= "foo" (::event/label span)))
        (is (inst? (::event/time event)))
        (is (= :warn (::event/level event)))
        (is (= "bar" (::event/label event)))
        (is (string? (::event/thread event)))
        (is (string? (::trace/trace-id event)))
        (is (= (::trace/trace-id span) (::trace/trace-id event)))
        (is (string? (::trace/span-id event)))
        (is (string? (::trace/span-id span)))
        (is (not= (::trace/span-id span) (::trace/span-id event)))
        (is (= (::trace/span-id span) (::trace/parent-id event)))))))


(deftest watch-sync-span
  (testing "with result"
    (capture-observed
      (let [result (ken/watch :alpha
                     :omega)]
        (is (= :omega result)
            "should return the watched expression value")
        (is (= 1 (count @observed))
            "should observe one event")
        (let [span1 (first @observed)]
          (is (= "alpha" (::event/label span1)))
          (is (number? (::event/duration span1)))
          (is (string? (::trace/trace-id span1)))
          (is (string? (::trace/span-id span1)))))))
  (testing "with error"
    (capture-observed
      (is (thrown? RuntimeException
            (ken/watch :alpha
              (throw (RuntimeException. "BOOM"))))
          "should rethrow the exception")
      (is (= 1 (count @observed))
          "should observe one event")
      (let [span1 (first @observed)]
        (is (= "alpha" (::event/label span1)))
        (is (number? (::event/duration span1)))
        (is (string? (::trace/trace-id span1)))
        (is (string? (::trace/span-id span1)))))))


(deftest watch-deferred-span
  (testing "with result"
    (capture-observed
      (let [d (d/deferred)
            result (ken/watch "one"
                     d)]
        (is (d/deferred? result)
            "should return a watched deferred value")
        (is (empty? @observed)
            "should not have observed any events yet")
        (d/success! d :fin)
        (is (= :fin @result)
            "evaluation should chain to result deferred")
        (is (= 1 (count @observed))
            "should observe one event after realization")
        (let [span1 (first @observed)]
          (is (= "one" (::event/label span1)))
          (is (number? (::event/duration span1)))
          (is (string? (::trace/trace-id span1)))
          (is (string? (::trace/span-id span1)))))))
  (testing "with error"
    (capture-observed
      (let [d (d/deferred)
            result (ken/watch "two"
                     d)]
        (is (d/deferred? result)
            "should return a watched deferred value")
        (is (empty? @observed)
            "should not have observed any events yet")
        (d/error! d (RuntimeException. "BOOM"))
        (is (thrown? RuntimeException
              @result)
            "exception should propagate to result deferred")
        (is (= 1 (count @observed))
            "should observe one event after realization")
        (let [span1 (first @observed)]
          (is (= "two" (::event/label span1)))
          (is (number? (::event/duration span1)))
          (is (string? (::trace/trace-id span1)))
          (is (string? (::trace/span-id span1))))))))


(deftest watch-completable-span
  (testing "with result"
    (capture-observed
      (let [cf (CompletableFuture.)
            result (ken/watch "happy"
                     cf)]
        (is (not (identical? cf result))
            "should not return the same value")
        (is (instance? CompletionStage result)
            "should return a watched completion stage")
        (is (empty? @observed)
            "should not have observed any events yet")
        (.complete cf :fin)
        (is (= :fin @result)
            "evaluation should chain to result")
        (is (= 1 (count @observed))
            "should observe one event after realization")
        (let [span1 (first @observed)]
          (is (= "happy" (::event/label span1)))
          (is (number? (::event/duration span1)))
          (is (string? (::trace/trace-id span1)))
          (is (string? (::trace/span-id span1)))))))
  (testing "with error"
    (capture-observed
      (let [cf (CompletableFuture.)
            result (ken/watch "sad"
                     cf)]
        (is (not (identical? cf result))
            "should not return the same value")
        (is (instance? CompletionStage result)
            "should return a watched completion stage")
        (is (empty? @observed)
            "should not have observed any events yet")
        (.completeExceptionally cf (RuntimeException. "BOOM"))
        (is (thrown? Exception
              @result)
            "exception should propagate to result")
        (is (= 1 (count @observed))
            "should observe one event after realization")
        (let [span1 (first @observed)]
          (is (= "sad" (::event/label span1)))
          (is (number? (::event/duration span1)))
          (is (string? (::trace/trace-id span1)))
          (is (string? (::trace/span-id span1))))))))


(deftest nested-watches
  (capture-observed
    (ken/watch "foo"
      (dotimes [_ 2]
        (ken/watch "bar"
          "do the thing")))
    (is (= 3 (count @observed))
        "should observe all events")
    (let [[bar1 bar2 foo :as spans] @observed]
      (is (= ["bar" "bar" "foo"] (map ::event/label spans))
          "events occur in expected order")
      (is (string? (::trace/trace-id foo))
          "root span has a trace-id")
      (is (apply = (map ::trace/trace-id spans))
          "all spans share the same trace")
      (is (= (::trace/span-id foo) (::trace/parent-id bar1))
          "bar1 is a child of foo")
      (is (= (::trace/span-id foo) (::trace/parent-id bar2))
          "bar2 is a child of foo")
      (is (not= (::trace/span-id bar1) (::trace/span-id bar2))
          "bar spans have distinct ids")
      (is (not (.isAfter (::event/time foo) (::event/time bar1)))
          "foo starts at or before bar1"))))


(deftest watch-sampling
  (capture-observed
    (with-redefs [trace/sample? (constantly false)]
      (ken/watch {::event/label "outer"
                  ::event/sample-rate 5}
        (ken/watch "inner"
          "do the thing"))
      (is (= 2 (count @observed))
          "should observe all events")
      (let [[inner outer :as spans] @observed]
        (is (= ["inner" "outer"] (map ::event/label spans))
            "events occur in expected order")
        (is (string? (::trace/trace-id outer))
            "root span has a trace-id")
        (is (apply = (map ::trace/trace-id spans))
            "all spans share the same trace")
        (is (= (::trace/span-id outer) (::trace/parent-id inner))
            "inner is a child of outer")
        (is (not= (::trace/span-id outer) (::trace/span-id inner))
            "spans have distinct ids")
        (is (false? (::trace/keep? outer))
            "outer span is sampled away")
        (is (false? (::trace/keep? inner))
            "inner span inherits sampling decision")))))


(deftest annotations
  (testing "annotate"
    (testing "with bad arguments"
      (is (thrown? IllegalArgumentException
            (ken/annotate 123)))
      (is (thrown? Exception
            (ken/annotate {::event/thread "haxin"}))))
    (testing "outside span"
      (is (nil? (ken/annotate {::foo "bar"}))))
    (testing "inside span"
      (capture-observed
        (ken/watch "foo"
          :do-a-thing
          (ken/annotate {::abc 123
                         ::def true}))
        (is (= 1 (count @observed)))
        (let [span (first @observed)]
          (is (= "foo" (::event/label span)))
          (is (= 123 (::abc span))
              "should set attributes")
          (is (= true (::def span)))))))
  (testing "time"
    (capture-observed
      (ken/watch "foo"
        (ken/time ::thinking
          (Thread/sleep 1)))
      (is (= 1 (count @observed)))
      (let [span (first @observed)]
        (is (= "foo" (::event/label span)))
        (is (<= 1.0 (::thinking span))
            "should set time value"))))
  (testing "error"
    (testing "with bad arguments"
      (is (thrown? IllegalArgumentException
            (ken/error 123))))
    (testing "with exception"
      (capture-observed
        (ken/watch "uh-oh"
          (ken/error (RuntimeException. "BOOM")))
        (is (= 1 (count @observed)))
        (let [span (first @observed)]
          (is (= "uh-oh" (::event/label span)))
          (is (instance? RuntimeException (::event/error span))
              "should set error value")
          (is (= "BOOM" (ex-message (::event/error span))))
          (is (true? (::event/fault? span))))))))
