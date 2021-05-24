(ns amperity.ken.context-test
  (:require
    [amperity.ken.context :as ken.ctx]
    [amperity.ken.core :as ken]
    [clojure.test :refer [deftest testing is use-fixtures]]))


(use-fixtures
  :each
  (fn [test-case]
    (ken.ctx/clear!)
    (test-case)))


(deftest collector-lifecycle
  (testing "clearing"
    (is (nil? (ken.ctx/clear!)))
    (is (empty? (ken.ctx/collect))))
  (testing "registration"
    (is (= ::foo (ken.ctx/register! ::foo (constantly {:foo true}))))
    (is (= {:foo true} (ken.ctx/collect)))
    (is (= ::bar (ken.ctx/register-static! ::bar {:bar 123})))
    (is (= {:foo true, :bar 123} (ken.ctx/collect))))
  (testing "unregistration"
    (is (nil? (ken.ctx/unregister! ::foo)))
    (is (= {:bar 123} (ken.ctx/collect)))
    (is (nil? (ken.ctx/unregister! ::bar)))
    (is (empty? (ken.ctx/collect)))))


(deftest collector-error
  (is (= ::err (ken.ctx/register! ::err (fn [] (throw (RuntimeException. "BOOM"))))))
  (is (= {} (ken.ctx/collect))))


(deftest local-context
  (testing "without collectors"
    (is (empty? (ken.ctx/collect)))
    (is (= {:baz 'abc}
           (ken/with-context {:baz 'abc}
             (ken.ctx/collect)))))
  (testing "with collectors"
    (is (= ::ken.ctx/static (ken.ctx/register-static! {:foo true, :bar 123})))
    (is (= {:foo true
            :bar 123
            :baz 'abc}
           (ken/with-context {:baz 'abc}
             (ken.ctx/collect))))
    (is (= {:foo true
            :bar 456}
           (ken/with-context {:bar 456}
             (ken.ctx/collect))))))
