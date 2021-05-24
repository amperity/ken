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


(defn await-tap
  "Wait for the tap to finish sending events for up to `n` attempts."
  [n]
  (loop [attempts n]
    (when (and (pos? attempts) (pos? (ken.tap/queue-size)))
      (Thread/sleep 10)
      (recur (dec attempts))))
  (when (pos? (ken.tap/queue-size))
    (throw (IllegalStateException.
             (str "tap queue still has " (ken.tap/queue-size)
                  " events after " (* n 10) " ms")))))


(deftest subscription-lifecycle
  (testing "clearing"
    (is (nil? (ken.tap/clear!))))
  (testing "subscription"
    (is (= ::any (ken.tap/subscribe! ::any any?)))
    (is (= ::prn (ken.tap/subscribe! ::prn prn))))
  (testing "unsubscription"
    (is (nil? (ken.tap/unsubscribe! ::any)))
    (is (nil? (ken.tap/unsubscribe! ::prn)))))


(deftest subscriber-error
  (is (= ::err (ken.tap/subscribe!
                 ::err
                 (fn [event] (throw (RuntimeException. "BOOM"))))))
  (is (true? (ken.tap/send {:a "thing"})))
  (await-tap 10))


(deftest publishing
  (let [results (atom [])
        record (partial swap! results conj)]
    (is (= ::record (ken.tap/subscribe! ::record record)))
    (is (zero? (ken.tap/queue-size)))
    (is (true? (ken.tap/send {:id 0})))
    (is (true? (ken.tap/send {:id 1})))
    ;; Wait for all events to be published.
    (await-tap 10)
    (is (= [{:id 0} {:id 1}] @results))))


(deftest blocking-drops
  (let [blocker (promise)
        record (fn [_] @blocker)]
    (is (= ::blocking (ken.tap/subscribe! ::blocking record)))
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
