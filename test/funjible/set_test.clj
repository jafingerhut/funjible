(ns funjible.set-test
  (:require [clojure.test :refer :all]
            [funjible.set :as f]))

(deftest throw-on-non-set-args
  (is (thrown? AssertionError (f/union [1 2])))
  (is (thrown? AssertionError (f/union #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/intersection [1 2])))
  (is (thrown? AssertionError (f/intersection #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/difference [1 2])))
  (is (thrown? AssertionError (f/difference #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/select nil? nil)))
  (is (thrown? AssertionError (f/select identity [nil 1 2])))

  (is (thrown? AssertionError (f/subset? #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (f/superset? #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/project [{:a 1 :b 2} {:a 3 :b 4}] [:a])))

  (is (thrown? AssertionError (f/rename-keys {:a 1 :b 2} [[:a 3] [:b 4]])))

  (is (thrown? AssertionError (f/rename {:a 1 :b 2} {:a 3, :b 4})))
  (is (thrown? AssertionError (f/rename #{{:a 1 :b 2}} [[:a 3] [:b 4]])))
  )
