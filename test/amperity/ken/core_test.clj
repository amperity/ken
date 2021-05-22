(ns amperity.ken.core-test
  (:require
    [amperity.ken.core :as ken]
    [amperity.ken.event :as event]
    [amperity.ken.tap :as tap]
    [amperity.ken.trace :as trace]
    [clojure.test :refer [deftest testing is]]
    [manifold.deferred :as d]))


(deftest watch-behavior
  (testing "basic wrapping"
    (let [observed (atom [])]
      (with-redefs [tap/send (fn [event _] (swap! observed conj event))]
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
            (is (string? (::trace/span-id span1))))))))
  (testing "deferred wrapping"
    (let [observed (atom [])]
      (with-redefs [tap/send (fn [event _] (swap! observed conj event))]
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
            (is (string? (::trace/span-id span1))))))))
  (testing "nested watches"
    (let [observed (atom [])]
      (with-redefs [tap/send (fn [event _] (swap! observed conj event))]
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
              "foo starts at or before bar1"))))))


(deftest watch-sampling
  (testing "nested watches"
    (let [observed (atom [])]
      (with-redefs [tap/send (fn [event _] (swap! observed conj event))
                    trace/sample? (constantly false)]
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
              "inner span inherits sampling decision"))))))
