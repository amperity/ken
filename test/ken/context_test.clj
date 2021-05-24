(ns ken.context-test
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [ken.context :as ctx]
    [ken.core :as ken]))


(use-fixtures
  :each
  (fn [test-case]
    (ctx/clear!)
    (test-case)))


(deftest collector-lifecycle
  (testing "clearing"
    (is (nil? (ctx/clear!)))
    (is (empty? (ctx/collect))))
  (testing "registration"
    (is (= ::foo (ctx/register! ::foo (constantly {:foo true}))))
    (is (= {:foo true} (ctx/collect)))
    (is (= ::bar (ctx/register-static! ::bar {:bar 123})))
    (is (= {:foo true, :bar 123} (ctx/collect))))
  (testing "unregistration"
    (is (nil? (ctx/unregister! ::foo)))
    (is (= {:bar 123} (ctx/collect)))
    (is (nil? (ctx/unregister! ::bar)))
    (is (empty? (ctx/collect)))))


(deftest collector-error
  (is (= ::err (ctx/register!
                 ::err
                 (fn [] (throw (RuntimeException. "BOOM"))))))
  (is (= {} (ctx/collect))))


(deftest local-context
  (testing "without collectors"
    (is (empty? (ctx/collect)))
    (is (= {:baz 'abc}
           (ken/with-context {:baz 'abc}
             (ctx/collect)))))
  (testing "with collectors"
    (is (= ::ctx/static (ctx/register-static! {:foo true, :bar 123})))
    (is (= {:foo true
            :bar 123
            :baz 'abc}
           (ken/with-context {:baz 'abc}
             (ctx/collect))))
    (is (= {:foo true
            :bar 456}
           (ken/with-context {:bar 456}
             (ctx/collect))))))
