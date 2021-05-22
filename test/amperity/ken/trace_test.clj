(ns amperity.ken.trace-test
  (:require
    [amperity.ken.event :as event]
    [amperity.ken.trace :as trace]
    [clojure.test :refer [deftest testing is]]))


(deftest sample?
  (testing "Make a sampling decision when a sample rate is provided"
    (is (some? (trace/sample? 1))
        "the sampling decision should return true or false")))


(deftest watch-span
  (testing "Make sure the right sampling context is contained in new spans"
    (trace/with-data (atom {::trace/keep? true})
      (let [data {::event/label "foo"
                  ::event/sample-rate 42}
            span (trace/watch-span data)]
        (is (contains? span ::trace/keep?)
            "the new span should contain the `::trace/keep?` key if `::trace/keep?` is already true")
        (is (true? (::trace/keep? span))
            "`::trace/keep?` in the new span's context should be true if `::trace/keep?` is already true")
        (is (not (contains? span ::event/sample-rate))
            "the new span should not contain the `::event/sample-rate` key if `::trace/keep` is already true")))
    (let [data {::event/label "foo"
                ::event/sample-rate 1}
          span (trace/watch-span data)]
      (is (not (true? (::trace/keep? span)))
          "when a sample rate is provided, and a sampling decision is made, the key `::trace/keep?` will never be true (only false or nil) in the new span when the trace is not being force-kept"))
    (let [data {::event/label "foo"}
          span (trace/watch-span data)]
      (is (not (contains? span ::trace/keep?))
          "When a sample rate is not provided, and the trace is not being force kept, `::trace/keep?` should not be present"))))
