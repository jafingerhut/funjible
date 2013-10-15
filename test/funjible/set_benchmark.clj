(ns funjible.set-benchmark
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [funjible.set :as fset]
            [funjible.set-precondition-mods-only :as fset-pre-only]
            [clojure.set :as cset]
            [avl.clj :as avl]
            [immutable-bitset :as bitset]
            [clojure.data.priority-map :as pm]
            [flatland.useful.deftype :as useful]
            [criterium.core :as criterium]))

(set! *warn-on-reflection* true)


(def ^:dynamic *auto-flush* true)

(defn printf-to-writer [w fmt-str & args]
  (binding [*out* w]
    (apply clojure.core/printf fmt-str args)
    (when *auto-flush* (flush))))

(defn iprintf [fmt-str-or-writer & args]
  (if (instance? CharSequence fmt-str-or-writer)
    (apply printf-to-writer *out* fmt-str-or-writer args)
    (apply printf-to-writer fmt-str-or-writer args)))

(defn die [fmt-str & args]
  (apply iprintf *err* fmt-str args)
  (System/exit 1))

(defn basename
  "If the string contains one or more / characters, return the part of
  the string after the last /.  If it contains no / characters, return
  the entire string."
  [s]
  (if-let [[_ base] (re-matches #".*/([^/]+)" s)]
    base
    s))



;; time units for values below are nanosec
(def s-to-ns (* 1000 1000 1000))

;; 100 msec
;;(def ^:dynamic *target-execution-time* (long (* 0.1 s-to-ns)))
;; criterium default = 1 sec
(def ^:dynamic *target-execution-time* (long (* 1.0 s-to-ns)))

(def ^:dynamic *sample-count* 30)
;(def ^:dynamic *sample-count* 5)

;; No warmup except the code that estimates the number of executions
;; of the expression needed to take about *target-execution-time*
;;(def ^:dynamic *warmup-jit-period* (long (*  0 s-to-ns)))
;; criterium default = 10 sec
(def ^:dynamic *warmup-jit-period* (long (* 10 s-to-ns)))
;(def ^:dynamic *warmup-jit-period* (long (* 5 s-to-ns)))


(defn num-digits-after-dec-point
  [x n]
  (loop [max-val (- 10.0 (/ 5.0 (apply * (repeat n 10.0))))
         digits-after-dec-point (dec n)]
    (if (or (zero? digits-after-dec-point)
            (< x max-val))
      digits-after-dec-point
      (recur (* max-val 10.0) (dec digits-after-dec-point)))))


(defn float-with-n-sig-figs
  "Assumes x >= 1.0 and n >= 1.  Returns a string that represents the
  value of x with at least n significant figures."
  [x n]
  (format (format "%%.%df" (num-digits-after-dec-point x n)) x))


(defn time-with-scale [time-sec]
  (let [[s units scale-fac]
        (cond (< time-sec 999.5e-15) [(format "%.2e sec" time-sec)]
              (< time-sec 999.5e-12) [nil "psec" 1e12]
              (< time-sec 999.5e-9)  [nil "nsec" 1e9]
              (< time-sec 999.5e-6)  [nil "usec" 1e6]
              (< time-sec 999.5e-3)  [nil "msec" 1e3]
              (< time-sec 999.5e+0)  [nil  "sec" 1  ]
              :else [(format "%.0f sec" time-sec)])]
    (if s
      s
      (str (float-with-n-sig-figs (* scale-fac time-sec) 3) " " units))))


(defn first-word [s]
  (first (str/split s #"\s+")))


(defn platform-desc [results]
  (let [os (:os-details results)
        runtime (:runtime-details results)]
    (format "Clojure %s / %s-bit %s JDK %s / %s %s"
            (:clojure-version-string runtime)
            (:sun-arch-data-model runtime)
            (first-word (:vm-vendor runtime))
            (:java-version runtime)
            (:name os) (:version os))))


(defmacro benchmark
  [desc-map bindings expr & opts]
  `(do
     (iprintf *err* "Benchmarking %s %s %s" ~desc-map '~bindings '~expr)
     ;(criterium/quick-bench ~expr ~@opts)
     ;(criterium/bench ~expr ~@opts)
     (let ~bindings
       (let [results#
;;             (criterium/with-progress-reporting
               (criterium/benchmark ~expr
                                    (merge {:samples *sample-count*
                                            :warmup-jit-period *warmup-jit-period*
                                            :target-execution-time *target-execution-time*}
                                           (hash-map ~@opts)))
;;               (criterium/quick-benchmark ~expr ~@opts)
;;               )
             ]
         (pp/pprint {:bindings '~bindings
                     :description ~desc-map
                     :expr '~expr
                     :opts '~opts
                     :results results#})
         (iprintf *err* " %s\n" (time-with-scale (first (:mean results#))))
;;         (iprintf *err* "    %s\n" (platform-desc results#))
         ))
     (flush)))


(defn report-from-benchmark-results-file [fname]
  (with-open [rdr (java.io.PushbackReader. (io/reader fname))]
    (loop [result (read rdr false :eof)]
      (when-not (= result :eof)
        (iprintf "\n\n")
        (iprintf "Benchmark %s\n" (:bindings result))
        (iprintf "    %s\n" (:expr result))
        (iprintf "    using %s\n" (platform-desc (:results result)))
        (criterium/report-result (:results result))
        (recur (read rdr false :eof))))))



;; Main factors in performance of union function with two sets as
;; args:

;; The larger set is the 'base', and the elements of the smaller set
;; are traversed one at a time.  For each one, that element is conj'ed
;; onto the base set (or conj!ed if a transient set is being used).

;; conj/conj! seems in most cases to be faster if the element is
;; already in the set.  That seems normal.  At least for Clojure's
;; PersistentHashSet, conj/conj! returns an identical set if the
;; element you 'add' is already in the set.  That should be faster
;; than allocating and initializing memory.

;; Converting the base set to a transient, conj!ing the elements of
;; the smaller set, and converting back to a persistent set should be
;; faster if the smaller set contains enough elements to overcome the
;; extra time required to do the transient/persistent conversions.
;; For any particular set type that has a transient implementation,
;; there should be some minimum size for the second smaller set for
;; which transients are faster (or at least usually faster), but below
;; that they are slower (or usually slower) than not using transients.

;; It is also reasonable for the run times to be longer if the base
;; set is larger, simply because conj'ing onto a larger set is more
;; work than conj'ing onto a small set, on average.

;; The benchmark tests below use sizes 0, 4, 20, and 1000 for the base
;; set, and sizes 0, 3, 4, 20, and 1000 for the second set.  For each
;; second set, we measure a case where the second set is a subset of
;; the base set, and where it is disjoint with the base set.  Those
;; are likely to be the extreme ends of a spectrum of run times, with
;; the disjoint case slower.

(deftest ^:benchmark benchmark-union-funjible.set-vs-clojure.set
  (iprintf *err* "\nfunjible.set/union vs. cojure.set/union\n")
  (doseq [ [f desc] [
                     [#(apply hash-set %) "clojure.core/hash-set"]
                     [#(apply sorted-set %) "clojure.core/sorted-set"]
                     [#(apply avl/sorted-set %) "avl.clj/sorted-set"]
                     [bitset/sparse-bitset "immutable-bitset/sparse-bitset"]
                     [bitset/dense-bitset "immutable-bitset/dense-bitset"]
                     ]
           [seq1 seq2]
           [
            [[] []]
            
            [(range 0    4) [0 1 2]]
            [(range 0    4) [0 1 2 3]]
            [(range 0    4) [1000 1001 1002]]
            [(range 0    4) [1000 1001 1002 1003]]
            
            [(range 0   20) [0 1 2]]
            [(range 0   20) [0 1 2 3]]
            [(range 0   20) (range 0 20)]
            [(range 0   20) [1000 1001 1002]]
            [(range 0   20) [1000 1001 1002 1003]]
            [(range 0   20) (range 1000 1020)]
            
            [(range 0 1000) [0 1 2]]
            [(range 0 1000) [0 1 2 3]]
            [(range 0 1000) (range 0 20)]
            [(range 0 1000) [1000 1001 1002]]
            [(range 0 1000) [1000 1001 1002 1003]]
            [(range 0 1000) (range 1000 1020)]
            [(range 0 1000) (range 1000 2000)]
            ]]
    (let [s1 (f seq1), s2 (f seq2)]
      ;;(printf "\n===== %s (type s1)=%s\n\n" desc (type s1))
      ;;(println "--- (clojure.set/union" s1 s2 ")")
      (benchmark {:fn "clojure.set/union" :set-type desc :args [s1 s2]}
                 [] (cset/union s1 s2))
      ;;(println "--- (funjible.set/union" s1 s2 ")")
      (benchmark {:fn "funjible.set/union" :set-type desc :args [s1 s2]}
                 [] (fset/union s1 s2))
      )))


;; intersection is implemented similarly to union, except the smaller
;; of two argument sets is used as the 'base' set, and the larger one
;; as the second set.  The elements of the smaller base set are
;; iterated over, and for any that are not contained in the second
;; set, they are disj'ed from the base set (or disj!ed if transients
;; are used).

;; Similar considerations apply as for union in whether transients
;; help improve the speed.  If the smaller base set is small enough, I
;; expect using transients to be slower, so we should only do so if
;; the smaller base set is larger than a certain threshold size.

;; As for union, I expect that two extremes for performance testing
;; should be (1) when the second larger set is a superset of the first
;; base set, and thus the result is equal to the base set, and (2)
;; when the second set is disjoint with the first set, and thus the
;; result is the empty set.  I would guess (2) would be slower, since
;; it must remove the elements.

(deftest ^:benchmark benchmark-intersection-funjible.set-vs-clojure.set
  (iprintf *err* "\nfunjible.set/intersection vs. cojure.set/intersection\n")
  (doseq [ [f desc] [
                     [#(apply hash-set %) "clojure.core/hash-set"]
                     [#(apply sorted-set %) "clojure.core/sorted-set"]
                     [#(apply avl/sorted-set %) "avl.clj/sorted-set"]
                     [bitset/sparse-bitset "immutable-bitset/sparse-bitset"]
                     [bitset/dense-bitset "immutable-bitset/dense-bitset"]
                     ]
           [seq1 seq2]
           [
            [[] []]
            
            [(range 0    2) (range 0 2)]    ; 2nd is superset, same size
            [(range 0    2) (range 0 1000)]  ; 2nd is superset, larger
            [(range 0    2) (range 1000 1002)]  ; 2nd is disjoint, small
            [(range 0    2) (range 1000 2000)]  ; 2nd is disjoint, larger
            
            [(range 0    3) (range 0 3)]    ; 2nd is superset, same size
            [(range 0    3) (range 0 1000)]  ; 2nd is superset, larger
            [(range 0    3) (range 1000 1003)]  ; 2nd is disjoint, small
            [(range 0    3) (range 1000 2000)]  ; 2nd is disjoint, larger
            
            [(range 0    4) (range 0 4)]    ; 2nd is superset, same size
            [(range 0    4) (range 0 1000)]  ; 2nd is superset, larger
            [(range 0    4) (range 1000 1004)]  ; 2nd is disjoint, small
            [(range 0    4) (range 1000 2000)]  ; 2nd is disjoint, larger
            
            [(range 0   20) (range 0 20)]
            [(range 0   20) (range 0 1000)]
            [(range 0   20) (range 1000 1020)]
            [(range 0   20) (range 1000 2000)]
            
            [(range 0 1000) (range 0 1000)]
            [(range 0 1000) (range 0 2000)]
            [(range 0 1000) (range 1000 2000)]
            [(range 0 1000) (range 1000 3000)]
            ]]
    (let [s1 (f seq1), s2 (f seq2)]
      ;;(iprintf *err* "\n===== %s (type s1)=%s\n\n" desc (type s1))
      ;;(iprintf *err* "--- (clojure.set/intersection %s %s)" s1 s2)
      (benchmark {:fn "clojure.set/intersection" :set-type desc :args [s1 s2]}
                 [] (cset/intersection s1 s2))
      ;;(iprintf *err* "--- (funjible.set/intersection %s %s)" s1 s2)
      (benchmark {:fn "funjible.set/intersection" :set-type desc :args [s1 s2]}
                 [] (fset/intersection s1 s2))
      )))


(comment
(deftest ^:benchmark benchmark-funjible.set-vs-clojure.set-subset?
  (iprintf *err* "\nfunjible.set/subset? vs. cojure.set/subset?\n")
  (doseq [[s1 s2] [[(hash-set) (hash-set)]
                   [(hash-set 1 2 3) (hash-set 4 5)]
                   [(hash-set 1 2 3) (hash-set 1 2 3 4)]
                   ]]
    ;;(println)
    ;;(println "(funjible.set/subset?" s1 s2 ")")
    (criterium/bench (fset/subset? s1 s2))
    ;;(println "(clojure.set/subset?" s1 s2 ")")
    (criterium/bench (cset/subset? s1 s2))))
)
