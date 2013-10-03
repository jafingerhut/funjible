(ns funjible.set-test
  (:require [clojure.test :refer :all]
            [funjible.set :as fset]
            [clojure.set :as cset]
            [avl.clj :as avl]
            [immutable-bitset :as bitset]
            [criterium.core :as c]))


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
  (is (thrown? AssertionError (fset/union [1 2])))
  (is (thrown? AssertionError (fset/union #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (fset/intersection [1 2])))
  (is (thrown? AssertionError (fset/intersection #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (fset/difference [1 2])))
  (is (thrown? AssertionError (fset/difference #{1 2 3} '(1 2))))

  (is (thrown? AssertionError (fset/select nil? nil)))
  (is (thrown? AssertionError (fset/select identity [nil 1 2])))

  (is (thrown? AssertionError (fset/project [{:a 1 :b 2} {:a 3 :b 4}] [:a])))

  (is (thrown? AssertionError (fset/rename-keys {:a 1 :b 2} [[:a 3] [:b 4]])))

  (is (thrown? AssertionError (fset/rename {:a 1 :b 2} {:a 3, :b 4})))
  (is (thrown? AssertionError (fset/rename #{{:a 1 :b 2}} [[:a 3] [:b 4]])))

  (is (thrown? AssertionError (fset/index {:a 1 :b 2} [:a])))

  (is (thrown? AssertionError (fset/map-invert #{{:a 1 :b 2}})))

  (is (thrown? AssertionError (fset/join {:a 1 :b 2} #{})))
  (is (thrown? AssertionError (fset/join #{} {:a 1 :b 2})))
  (is (thrown? AssertionError (fset/join {:a 1 :b 2} #{} {})))
  (is (thrown? AssertionError (fset/join #{} {:a 1 :b 2} {})))
  (is (thrown? AssertionError (fset/join #{} #{{:a 1 :b 2}} [:a])))

  (is (thrown? AssertionError (fset/subset? #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (fset/superset? #{1 2 3} '(1 2))))
  )


(comment
(deftest ^:benchmark benchmark-funjible.set-vs-clojure.set-union
  (println "\nfunjible.set/union vs. cojure.set/union")
  (doseq [[s1 s2] [[(hash-set) (hash-set)]
                   [(hash-set 1 2 3) (hash-set 4 5)]
                   ]]
    (println)
    (println "(funjible.set/union" s1 s2 ")")
    (c/bench (fset/union s1 s2))
    (println "(clojure.set/union" s1 s2 ")")
    (c/bench (cset/union s1 s2))))
)


(deftest ^:benchmark benchmark-funjible.set-vs-clojure.set-subset?
  (println "\nfunjible.set/subset? vs. cojure.set/subset?")
  (doseq [[s1 s2] [[(hash-set) (hash-set)]
                   [(hash-set 1 2 3) (hash-set 4 5)]
                   [(hash-set 1 2 3) (hash-set 1 2 3 4)]
                   ]]
    (println)
    (println "(funjible.set/subset?" s1 s2 ")")
    (c/bench (fset/subset? s1 s2))
    (println "(clojure.set/subset?" s1 s2 ")")
    (c/bench (cset/subset? s1 s2))))
