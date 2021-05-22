(ns amperity.ken.tap-test
  (:require
    [amperity.ken.tap :as ken.tap]
    [clojure.test :refer [deftest testing is use-fixtures]]))


(use-fixtures
  :each
  (fn [test-case]
    (ken.tap/clear!)
    (ken.tap/drain!)
    (test-case)))


(deftest subscription-lifecycle
  (testing "clearing"
    (is (nil? (ken.tap/clear!))))
  (testing "subscription"
    (is (nil? (ken.tap/subscribe! ::any any?)))
    (is (nil? (ken.tap/subscribe! ::prn prn))))
  (testing "unsubscription"
    (is (nil? (ken.tap/unsubscribe! ::any)))
    (is (nil? (ken.tap/unsubscribe! ::prn)))))


(deftest publishing
  (let [results (atom [])
        record (partial swap! results conj)]
    (is (nil? (ken.tap/subscribe! ::record record)))
    (is (zero? (ken.tap/queue-size)))
    (is (true? (ken.tap/send {:id 0})))
    (is (true? (ken.tap/send {:id 1})))
    ;; Wait for all events to be published.
    (loop [attempts 10]
      (when (and (pos? attempts) (pos? (ken.tap/queue-size)))
        (Thread/sleep 10)
        (recur (dec attempts))))
    (is (= [{:id 0} {:id 1}] @results))))


(deftest blocking-drops
  (let [blocker (promise)
        record (fn [_] @blocker)]
    (is (nil? (ken.tap/subscribe! ::blocking record)))
    (is (zero? (ken.tap/queue-size)))
    (is (true? (ken.tap/send {:id 0}))
        "immediate send should return true")
    (dotimes [i 1200]
      (ken.tap/send {:id i}))
    (is (false? (ken.tap/send {:id 0}))
        "dropped send should return false")
    (is (< 1000 (ken.tap/queue-size)))
    (deliver blocker :ok)
    (is (true? (ken.tap/send {:id :fin} 1000))
        "queue should clear")))
