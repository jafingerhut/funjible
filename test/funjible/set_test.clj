(ns funjible.set-test
  (:require [clojure.test :refer :all]
            [funjible.set-no-patching :as fset]
            [funjible.set-clj190-precondition-mods-only :as fset-pre-only]
            [funjible.set-precondition-always-transient-mods :as fset-trans]
            [clojure.set :as cset]
            [clojure.data.avl :as avl]
            [clojure.data.int-map :as imap]
            [clojure.data.priority-map :as pm]
            [flatland.useful.deftype :as useful]
            [clojure.pprint :as pp]))


;; Many other types besides numbers, strings, and keywords have values
;; that can all be compared to each other, but for testing purposes
;; simply consider these types to be comparable.

(defn mutually-comparable? [s]
  (or (every? number? s)
      (every? string? s)
      (every? keyword? s)))

(defn reverse-compare [a b]
  (let [c (compare a b)]
    (cond (zero? c) 0
          (neg? c) 1
          :else -1)))

;; Some set types, and the types of elements they can contain:
;;
;; clojure.core/
;;     set            any
;;     hash-set       any
;;     sorted-set     any comparable by clojure.core/compare
;;     sorted-set-by  any comparable by provided compare fn
;; clojure.data.avl/
;;     sorted-set     any comparable by clojure.core/compare
;;     sorted-set-by  any comparable by provided compare fn
;; clojure.data.int-map/
;;     int-set        any integer in range of Long
;;     dense-int-set  any integer in range of Long

(defn legal-sets-from-elements-in-seq [s]
  (concat [ (set s)
            (apply hash-set s) ]
          (if (mutually-comparable? s)
            [ (apply sorted-set s)
              (apply sorted-set-by reverse-compare s)
              (apply avl/sorted-set s)
              (apply avl/sorted-set-by reverse-compare s) ])
          (if (every? #(and (number? %) (<= Long/MIN_VALUE % Long/MAX_VALUE)) s)
            [ (imap/int-set s)
              (imap/dense-int-set s) ])))


;; Some map types, and the types of keys they can contain:
;;
;; clojure.core/
;;     array-map       any
;;     hash-map        any
;;     sorted-map      any comparable by clojure.core/compare
;;     sorted-map-by   any comparable by provided compare fn
;; clojure.data.priority-map/
;;     priority-map    any, but the _values_ must be comparable by clojure.core/compare
;;     priority-map-by any, but the _values_ must be comparable by provided compare fn
;; flatland.useful.deftype/
;;     alist
;; flatland.useful.map/
;;     ordering-map    any.  Leave it out because: (1) it needs extra
;;                           arg to specify the order of some selected
;;                           keys, and (2) the underlying
;;                           implementation is just a
;;                           clojure.core/sorted-map-by with a custom
;;                           comparison function.
(defn legal-maps-from-pairs-in-seq [seq-of-pairs]
  (concat [ (into (array-map) seq-of-pairs)
            (into (hash-map) seq-of-pairs) ]
          (if (mutually-comparable? (map first seq-of-pairs))
           [ (into (sorted-map) seq-of-pairs) ])
          (if (mutually-comparable? (map second seq-of-pairs))
           [ (into (pm/priority-map) seq-of-pairs) ])
          ;; useful/alist seems buggy in latest 0.10.3 release of
          ;; library.  Check again later.
          ;;[ (useful/alist
          ))


(defn apply-fns [fns seq-of-args]
  (mapcat (fn [args]
            (map (fn [f] {:args args :f f :result (apply f args)})
                 fns))
          seq-of-args))


(deftest they-are-all-sets
  (let [sets-with-transient-impl [(hash-set 1 2 3)
                                  (set [1 2 3])
                                  (avl/sorted-set 1 2 3)
                                  (avl/sorted-set-by > 1 2 3)
                                  (imap/int-set [1 2 3])
                                  (imap/dense-int-set [1 2 3])]
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
      (is (thrown? ClassCastException (transient s)))))

  (doseq [s [ []
              [1 2 3]
              (range 1000)
              (concat (range 15 30) (range 1000000 1000010)) ]]
    (let [sets (legal-sets-from-elements-in-seq s)]
      (doseq [s1 sets, s2 sets]
        (let [msg (format "(type s1)=%s (type s2)=%s" (type s1) (type s2))]
          (is (= s1 s2) msg)
          (is (= (hash s1) (hash s2)) msg))))))


(deftest throw-on-non-set-args
  (is (thrown? AssertionError (fset/union [1 2])))
  (is (thrown? AssertionError (fset/union #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (fset/union #{1 2 3} #{4 5} '(1 2))))

  (is (thrown? AssertionError (fset/intersection [1 2])))
  (is (thrown? AssertionError (fset/intersection #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (fset/intersection #{1 2 3} #{4 5} '(1 2))))

  (is (thrown? AssertionError (fset/difference [1 2])))
  (is (thrown? AssertionError (fset/difference #{1 2 3} '(1 2))))
  (is (thrown? AssertionError (fset/difference #{1 2 3} #{4 5} '(1 2))))

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
  (is (thrown? AssertionError (fset/superset? #{1 2 3} '(1 2)))))


(deftest test-set-union-intersection-difference
  (doseq [[s1 s2] [
                   ;; 2 sets, at least one is empty
                   [[] []]
                   [[] [1]]
                   [[] [1 2 3]]
                   [[1] []]
                   [[1 2 3] []]
                   
                   ;; 2 sets
                   [[1] [2]]
                   [[1] [1 2]]
                   [[2] [1 2]]
                   [[1 2] [3]]
                   [[1 2] [2 3]]
                   [[1 2] [1 2]]
                   [[1 2] [3 4]]
                   [[1 2] [1]]
                   [[1 2] [2]]
                   [[1 2 4] [2 3 4 5]]

                   ;; different data types
                   [[1 2 :a :b nil] [false true \c "abc" [] [1 2]]]

                   [[1 2 3] [3]]
                   [[] [1]]
                   [[10 2000000 50000000] [2000000 -3]]
                   [[{:a 1 :b 2} "x"] ["x" "y" {:a 1 :b 2}]]
                   ]]
    
    ;; 1. Create all of the types of sets that are able to hold the
    ;; elements in s1.

    ;; 2. Then do the same for s2.

    ;; 3. For all pairs of sets created in 1 and 2, calculate the
    ;; union.

    ;; 4. Make sure all such sets are equal to each other.
    
    ;; 5. Do the same for intersection and difference.
    
    (let [sets1 (legal-sets-from-elements-in-seq s1)
          sets2 (legal-sets-from-elements-in-seq s2)
          all-set-pairs (for [x1 sets1, x2 sets2] [x1 x2])
          all-unions (apply-fns [fset/union fset-pre-only/union fset-trans/union
                                 cset/union]
                                all-set-pairs)
          all-intersections (apply-fns [fset/intersection
                                        fset-pre-only/intersection
                                        fset-trans/intersection
                                        cset/intersection]
                                       all-set-pairs)
          all-differences (apply-fns [fset/difference fset-pre-only/difference
                                      fset-trans/difference cset/difference]
                                     all-set-pairs)]
      (printf "%d set pairs for s1=%s s2=%s\n"
              (count all-set-pairs) s1 s2)
      (flush)
      (is (apply = (map :result all-unions)))
      (is (apply = (map :result all-intersections)))
      (doseq [x (concat all-unions all-intersections)]
        (is (contains? (set (map meta (:args x))) (meta (:result x))))
        (is (contains? (set (map sorted? (:args x))) (sorted? (:result x)))))
      (is (apply = (map :result all-differences)))
      (doseq [x all-differences]
        (is (= (meta (:result x)) (meta (first (:args x)))))
        (is (= (sorted? (:result x)) (sorted? (first (:args x))))))
      )))


(deftest test-select
  (doseq [[pred xset exp-result] [ [ integer? #{} #{} ]
                                   [ integer? #{1 2} #{1 2} ]
                                   [ integer? #{1 2 :a :b :c} #{1 2} ]
                                   [ integer? #{:a :b :c} #{} ]
                                   ]]
    (let [sets (legal-sets-from-elements-in-seq xset)
          all-selects (apply-fns [fset/select fset-pre-only/select
                                  fset-trans/select cset/select]
                                 (map (fn [s] [pred s]) sets))
          all-selects-preserving-sortedness (apply-fns [fset/select
                                                        fset-trans/select]
                                                       (map (fn [s] [pred s]) sets))]
      (doseq [x all-selects]
        (is (= exp-result (:result x)))
        (is (= (meta exp-result) (meta (first (:args x))))))
      (doseq [x all-selects-preserving-sortedness]
        (is (= (sorted? (:result x)) (sorted? (second (:args x)))))))))


(def compositions
  #{{:name "Art of the Fugue" :composer "J. S. Bach"}
    {:name "Musical Offering" :composer "J. S. Bach"}
    {:name "Requiem" :composer "Giuseppe Verdi"}
    {:name "Requiem" :composer "W. A. Mozart"}})


(deftest test-project
  (doseq [[xrel ks exp-result]
          [ [ compositions [:name] #{{:name "Art of the Fugue"}
                                     {:name "Requiem"}
                                     {:name "Musical Offering"}} ]
            [ compositions [:composer] #{{:composer "W. A. Mozart"}
                                         {:composer "Giuseppe Verdi"}
                                         {:composer "J. S. Bach"}} ]
            [ compositions [:year] #{{}} ]
            [ #{{}} [:name] #{{}} ]
            ]]
    (let [sets (legal-sets-from-elements-in-seq xrel)
          all-projects (apply-fns [fset/project fset-pre-only/project
                                   fset-trans/project cset/project]
                                  (map (fn [s] [s ks]) sets))]
      (doseq [x all-projects]
        (is (= exp-result (:result x)))
        (is (= (meta exp-result) (meta (first (:args x)))))
        (is (= (sorted? (:result x)) (sorted? (first (:args x)))))))))


(deftest test-rename-keys
   (doseq [[rename kmap exp-result]
           [ [ {:a "one" :b "two"} {:a :z} {:z "one" :b "two"} ]
             [ {:a "one" :b "two"} {:a :z :c :y} {:z "one" :b "two"} ]
             [ {:a "one" :b "two" :c "three"} {:a :b :b :a} {:a "two" :b "one" :c "three"} ]
             ]]
     (let [maps (legal-maps-from-pairs-in-seq rename)
           all-rename-keys (apply-fns [fset/rename-keys
                                       fset-pre-only/rename-keys
                                       fset-trans/rename-keys cset/rename-keys]
                                      (map (fn [m] [m kmap]) maps))]
       (doseq [x all-rename-keys]
         (is (= exp-result (:result x)))
         (is (= (meta (:result x)) (meta (first (:args x)))))
         (is (= (sorted? (:result x)) (sorted? (first (:args x)))))))))


(deftest test-rename
   (doseq [[xrel kmap exp-result]
           [ [ compositions {:name :title}
              #{{:title "Art of the Fugue" :composer "J. S. Bach"}
                {:title "Musical Offering" :composer "J. S. Bach"}
                {:title "Requiem" :composer "Giuseppe Verdi"}
                {:title "Requiem" :composer "W. A. Mozart"}} ]
             [ compositions {:year :decade}
              #{{:name "Art of the Fugue" :composer "J. S. Bach"}
                {:name "Musical Offering" :composer "J. S. Bach"}
                {:name "Requiem" :composer "Giuseppe Verdi"}
                {:name "Requiem" :composer "W. A. Mozart"}} ]
             [ #{{}} {:year :decade} #{{}} ] ]]
     (let [sets (legal-sets-from-elements-in-seq xrel)
           all-renames (apply-fns [fset/rename fset-pre-only/rename
                                   fset-trans/rename cset/rename]
                                  (map (fn [s] [s kmap]) sets))]
       (doseq [x all-renames]
         (is (= exp-result (:result x)))
         (is (= (meta (:result x)) (meta (first (:args x)))))
         (is (= (sorted? (:result x)) (sorted? (first (:args x)))))))))


(deftest test-index
  (doseq [[xrel ks exp-result]
          [ [ #{} [] {} ]
            [ #{{:c 2} {:b 1} {:a 1 :b 2}} [:b]
              {{:b 2} #{{:a 1 :b 2}}, {:b 1} #{{:b 1}} {} #{{:c 2}}} ] ]]
    (let [sets (legal-sets-from-elements-in-seq xrel)
          all-indexes (apply-fns [fset/index fset-pre-only/index
                                  fset-trans/index cset/index]
                                 (map (fn [s] [s ks]) sets))
          all-indexes-preserving-sortedness (apply-fns [fset/index
                                                        fset-trans/index]
                                                       (map (fn [s] [s ks]) sets))]
      (doseq [x all-indexes]
        (is (= exp-result (:result x)))
        (is (cset/subset? (set (map meta (vals exp-result))) #{ (meta (first (:args x))) })))
      (doseq [x all-indexes-preserving-sortedness]
        (is (= (sorted? (:result x)) (sorted? (second (:args x)))))))))


(deftest test-map-invert
  (doseq [[m exp-result]
          [ [ {:a "one" :b "two"} {"one" :a "two" :b} ] ]]
    (let [maps (legal-maps-from-pairs-in-seq m)
          all-inverts (apply-fns [fset/map-invert fset-pre-only/map-invert
                                  fset-trans/map-invert cset/map-invert]
                                 (map (fn [m] [m]) maps))]
      (doseq [x all-inverts]
        (is (= exp-result (:result x)))
        ;; Neither metadata nor sortedness of input map are preserved.
        ))))


(deftest test-join
  (doseq [[xrel1 xrel2 exp-result]
          [ [ #{} #{} #{} ]
            [ compositions compositions compositions ]
            [ compositions
             #{{:name "Art of the Fugue" :genre "Classical"}}
             #{{:name "Art of the Fugue" :composer "J. S. Bach" :genre "Classical"}} ] ]]
    (let [sets1 (legal-sets-from-elements-in-seq xrel1)
          sets2 (legal-sets-from-elements-in-seq xrel2)
          all-set-pairs (for [x1 sets1, x2 sets2] [x1 x2])
          all-joins (apply-fns [fset/join fset-pre-only/join fset-trans/join
                                cset/join]
                               all-set-pairs)
          all-joins-preserving-metadata-sortedness (apply-fns [fset/join
                                                               fset-trans/join]
                                                              all-set-pairs)]
      (doseq [x all-joins]
        (is (= exp-result (:result x))))
      (doseq [x all-joins-preserving-metadata-sortedness]
        (is (= (meta (:result x)) (meta (first (:args x)))))
        ;; not true with current implementation of join
        ;;(is (= (sorted? (:result x)) (sorted? (first (:args x)))))
        ))))


(deftest test-subset?-superset?
  (doseq [[maybe-sub maybe-super exp-result]
          [
           ;; The first of these are subsets of the second
           [ #{} #{} true ]
           [ #{} #{1} true ]
           [ #{1} #{1} true ]
           [ #{1 2} #{1 2} true ]
           [ #{1 2} #{1 2 42} true ]
           [ #{false} #{false} true ]
           [ #{nil}   #{nil} true ]
           [ #{nil}   #{nil false} true ]
           [ #{1 2 nil} #{1 2 nil 4} true ]
           ;; The first of these are not subsets of the second
           [ #{1} #{} false ]
           [ #{2} #{1} false ]
           [ #{1 3} #{1} false ]
           [ #{nil} #{false} false ]
           [ #{false} #{nil} false ]
           [ #{false nil} #{nil} false ]
           [ #{1 2 nil}   #{1 2} false ] ]]
    (let [sets1 (legal-sets-from-elements-in-seq maybe-sub)
          sets2 (legal-sets-from-elements-in-seq maybe-super)
          all-set-pairs (for [x1 sets1, x2 sets2] [x1 x2])
          all-subsets? (apply-fns [fset/subset? fset-pre-only/subset?
                                   fset-trans/subset? cset/subset?]
                                  all-set-pairs)
          all-supersets? (apply-fns [fset/superset? fset-pre-only/superset?
                                     fset-trans/superset? cset/superset?]
                                    (map reverse all-set-pairs))]
      (doseq [x (concat all-subsets? all-supersets?)]
        (is (= exp-result (:result x)))))))
