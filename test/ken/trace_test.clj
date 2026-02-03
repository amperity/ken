(ns ken.trace-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [ken.event :as event]
    [ken.trace :as trace]))


(deftest trace-data
  (testing "no context"
    (trace/with-data nil
      (is (nil? (trace/current-data)))
      (is (nil? (trace/current-trace-id)))
      (is (nil? (trace/current-span-id)))))
  (testing "some properties"
    (trace/with-data (atom {::trace/trace-id "thetrace"})
      (is (= {::trace/trace-id "thetrace"} (trace/current-data)))
      (is (= "thetrace" (trace/current-trace-id)))
      (is (nil? (trace/current-span-id)))))
  (testing "all properties"
    (trace/with-data (atom {::trace/trace-id "thetrace"
                            ::trace/span-id "thespan"
                            ::trace/keep? false})
      (is (= {::trace/trace-id "thetrace"
              ::trace/span-id "thespan"
              ::trace/keep? false}
             (trace/current-data)))
      (is (= "thetrace" (trace/current-trace-id)))
      (is (= "thespan" (trace/current-span-id))))))


(deftest trace-child
  (testing "without context"
    (with-redefs [trace/gen-trace-id (constantly "trace123")
                  trace/gen-span-id (constantly "span456")]
      (trace/with-data nil
        (is (= {::trace/trace-id "trace123"
                ::trace/span-id "span456"}
               (trace/child-attrs))))))
  (testing "explicit data"
    (with-redefs [trace/gen-span-id (constantly "span789")]
      (is (= {::trace/trace-id "trace123"
              ::trace/parent-id "span456"
              ::trace/span-id "span789"
              ::trace/keep? false}
             (trace/child-attrs
               {::trace/trace-id "trace123"
                ::trace/span-id "span456"
                ::trace/keep? false}))))))


(deftest sampling
  (testing "random sampling"
    (is (some? (trace/sample? 1))
        "should return true or false"))
  (testing "maybe sample"
    (testing "event without sampling properties"
      (is (= {:foo 123} (trace/maybe-sample {:foo 123}))
          "should be left alone"))
    (testing "event with keep"
      (is (= {:foo 123
              ::trace/keep? true}
             (trace/maybe-sample
               {:foo 123
                ::trace/keep? true}))
          "should preserve other keys")
      (is (= {::trace/keep? false
              ::trace/sample-rate 10}
             (trace/maybe-sample
               {::trace/keep? false
                ::event/sample-rate 10}))
          "should replace ::event/sample-rate with ::trace/sample-rate"))
    (testing "event with sample rate"
      (testing "selected to keep"
        (with-redefs [trace/sample? (constantly true)]
          (is (= {:bar "baz"
                  ::event/sample-rate 10}
                 (trace/maybe-sample
                   {:bar "baz"
                    ::event/sample-rate 10}))
              "should not set keep flag")))
      (testing "selected to discard"
        (with-redefs [trace/sample? (constantly false)]
          (is (= {:bar "baz"
                  ::event/sample-rate 10
                  ::trace/keep? false}
                 (trace/maybe-sample
                   {:bar "baz"
                    ::event/sample-rate 10}))
              "should set keep flag to false"))))))


(deftest parent-header-values
  (testing "formatting"
    (is (nil? (trace/format-parent-header {})))
    (is (nil? (trace/format-parent-header
                {::trace/span-id "00f067aa0ba902b7"}))
        "missing trace-id should not create a header")
    (is (nil? (trace/format-parent-header
                {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
                 ::trace/span-id ""}))
        "blank span-id should not create a header")
    (is (= "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
           (trace/format-parent-header
             {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
              ::trace/span-id "00f067aa0ba902b7"})))
    (is (= "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
           (trace/format-parent-header
             {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
              ::trace/span-id "00f067aa0ba902b7"
              ::trace/keep? true}))
        "keep=true sets sampled flag")
    (is (= "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
           (trace/format-parent-header
             {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
              ::trace/span-id "00f067aa0ba902b7"
              ::trace/keep? false}))
        "keep=false is not propagated in sampled flag"))
  (testing "parsing"
    (is (nil? (trace/parse-parent-header nil)))
    (is (nil? (trace/parse-parent-header "")))
    (is (nil? (trace/parse-parent-header "x-123-456")))
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"}
           (trace/parse-parent-header "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00")))
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"}
           (trace/parse-parent-header "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-xy"))
        "bad flags should be ignored")
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"}
           (trace/parse-parent-header "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-06"))
        "unknown flags should be ignored")
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"
            ::trace/keep? true}
           (trace/parse-parent-header "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
        "sampled flag sets keep"))
  (testing "round-trip"
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"}
           (-> {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
                ::trace/span-id "00f067aa0ba902b7"}
               (trace/format-parent-header)
               (trace/parse-parent-header))))
    (is (= {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
            ::trace/span-id "00f067aa0ba902b7"
            ::trace/keep? true}
           (-> {::trace/trace-id "4bf92f3577b34da6a3ce929d0e0e4736"
                ::trace/span-id "00f067aa0ba902b7"
                ::trace/keep? true}
               (trace/format-parent-header)
               (trace/parse-parent-header))))))
