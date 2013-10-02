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
  )
