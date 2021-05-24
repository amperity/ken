(ns ken.tap-test
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [ken.tap :as tap]))


(use-fixtures
  :each
  (fn [test-case]
    (tap/clear!)
    (tap/drain!)
    (test-case)))


(defn await-tap
  "Wait for the tap to finish sending events for up to `n` attempts."
  [n]
  (loop [attempts n]
    (when (and (pos? attempts) (pos? (tap/queue-size)))
      (Thread/sleep 10)
      (recur (dec attempts))))
  (when (pos? (tap/queue-size))
    (throw (IllegalStateException.
             (str "tap queue still has " (tap/queue-size)
                  " events after " (* n 10) " ms")))))


(deftest subscription-lifecycle
  (testing "clearing"
    (is (nil? (tap/clear!))))
  (testing "subscription"
    (is (= ::any (tap/subscribe! ::any any?)))
    (is (= ::prn (tap/subscribe! ::prn prn))))
  (testing "unsubscription"
    (is (nil? (tap/unsubscribe! ::any)))
    (is (nil? (tap/unsubscribe! ::prn)))))


(deftest subscriber-error
  (is (= ::err (tap/subscribe!
                 ::err
                 (fn [_] (throw (RuntimeException. "BOOM"))))))
  (is (true? (tap/send {:a "thing"})))
  (await-tap 10))


(deftest publishing
  (let [results (atom [])
        record (partial swap! results conj)]
    (is (= ::record (tap/subscribe! ::record record)))
    (is (zero? (tap/queue-size)))
    (is (true? (tap/send {:id 0})))
    (is (true? (tap/send {:id 1})))
    ;; Wait for all events to be published.
    (await-tap 10)
    (is (= [{:id 0} {:id 1}] @results))))


(deftest blocking-drops
  (let [blocker (promise)
        record (fn [_] @blocker)]
    (is (= ::blocking (tap/subscribe! ::blocking record)))
    (is (zero? (tap/queue-size)))
    (is (true? (tap/send {:id 0}))
        "immediate send should return true")
    (dotimes [i 1200]
      (tap/send {:id i}))
    (is (false? (tap/send {:id 0}))
        "dropped send should return false")
    (is (< 1000 (tap/queue-size)))
    (deliver blocker :ok)
    (is (true? (tap/send {:id :fin} 1000))
        "queue should clear")))
