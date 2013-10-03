(ns funjible.set-test
  (:require [clojure.test :refer :all]
            [funjible.set :as f]
            [avl.clj :as avl]
            [immutable-bitset :as bitset]))


(deftest they-are-all-sets
  (let [sets-with-transient-impl [(hash-set 1 2 3)
                                  (set [1 2 3])
                                  (avl/sorted-set 1 2 3)
                                  (avl/sorted-set-by > 1 2 3)
                                  (bitset/sparse-bitset [1 2 3])
                                  (bitset/dense-bitset [1 2 3])]
        sets-without-transient-impl [(sorted-set 1 2 3)
                                     (sorted-set-by > 1 2 3)]
        sets-all (concat sets-with-transient-impl sets-without-transient-impl)]
    (doseq [s sets-all]
      (is (set? s)))
    (doseq [s1 sets-all]
      (doseq [s2 sets-all]
        (is (= s1 s2))))
    (doseq [s sets-with-transient-impl]
      (is (instance? clojure.lang.IEditableCollection s))
      (is (= s (persistent! (transient s)))))
    (doseq [s sets-without-transient-impl]
      (is (thrown? ClassCastException (transient s))))))


(deftest throw-on-non-set-args
  (is (thrown? AssertionError (f/union [1 2])))
  (is (thrown? AssertionError (f/union #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/intersection [1 2])))
  (is (thrown? AssertionError (f/intersection #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/difference [1 2])))
  (is (thrown? AssertionError (f/difference #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (f/select nil? nil)))
  (is (thrown? AssertionError (f/select identity [nil 1 2])))

  (is (thrown? AssertionError (f/project [{:a 1 :b 2} {:a 3 :b 4}] [:a])))

  (is (thrown? AssertionError (f/rename-keys {:a 1 :b 2} [[:a 3] [:b 4]])))

  (is (thrown? AssertionError (f/rename {:a 1 :b 2} {:a 3, :b 4})))
  (is (thrown? AssertionError (f/rename #{{:a 1 :b 2}} [[:a 3] [:b 4]])))

  (is (thrown? AssertionError (f/index {:a 1 :b 2} [:a])))

  (is (thrown? AssertionError (f/map-invert #{{:a 1 :b 2}})))

  (is (thrown? AssertionError (f/join {:a 1 :b 2} #{})))
  (is (thrown? AssertionError (f/join #{} {:a 1 :b 2})))
  (is (thrown? AssertionError (f/join {:a 1 :b 2} #{} {})))
  (is (thrown? AssertionError (f/join #{} {:a 1 :b 2} {})))
  (is (thrown? AssertionError (f/join #{} #{{:a 1 :b 2}} [:a])))

  (is (thrown? AssertionError (f/subset? #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (f/superset? #{1 2 3} '(1 2))))
  )
