(ns funjible.set-benchmark
  (:import [java.io PushbackReader])
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [funjible.set :as fset]
            [funjible.set-precondition-mods-only :as fset-pre-only]
            [funjible.set-precondition-always-transient-mods :as fset-trans]
            [clojure.set :as cset]
            [clojure.data.avl :as avl]
            [clojure.data.int-map :as imap]
            [clojure.data.priority-map :as pm]
            ;[flatland.useful.deftype :as useful]
            [flatland.useful.map :as umap]
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


(defn args-from-table-form [table-form]
  (remove (fn [x] (or (nil? x) (string? x)))
          (apply concat table-form)))


(defn range-spec-to-set [rs]
  (let [[min max] rs]
    (set (range min max))))


(defn transform-one-arg-vec [f maybe-arg-vec]
  (if (and (vector? maybe-arg-vec) (every? vector? maybe-arg-vec))
    (mapv f maybe-arg-vec)
    maybe-arg-vec))


(defn replace-range-specs-in-table-form [table-form]
  (mapv (fn [row]
          (mapv #(transform-one-arg-vec range-spec-to-set %) row))
        table-form))


;; A list of set types and functions to construct them, that take a
;; seq-able collection as its only arg.

(def set-fn-and-descs [
                       [#(apply hash-set %) "clojure.core/hash-set"]
                       [#(apply sorted-set %) "clojure.core/sorted-set"]
                       [#(apply avl/sorted-set %) "clojure.data.avl/sorted-set"]
                       [imap/int-set "clojure.data.int-map/int-set"]
                       [imap/dense-int-set "clojure.data.int-map/dense-int-set"]
                       ])

;; Just abbreviations for ranges of integers

(def r0-2       (set (range    0    2)))
(def r0-3       (set (range    0    3)))
(def r0-4       (set (range    0    4)))
(def r0-20      (set (range    0   20)))
(def r0-100     (set (range    0  100)))
(def r0-1000    (set (range    0 1000)))
(def r0-3000    (set (range    0 3000)))
(def r1000-1002 (set (range 1000 1002)))
(def r1000-1003 (set (range 1000 1003)))
(def r1000-1004 (set (range 1000 1004)))
(def r1000-1020 (set (range 1000 1020)))
(def r1000-1100 (set (range 1000 1100)))
(def r1000-2000 (set (range 1000 2000)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Benchmarks for: union
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def union-results-table-form [
  [ "2nd arg &rarr;" "#{}"   "#{0..2}"      "#{0..3}"      "#{0..19}"      "#{0..99}"       "#{0..999}"       "#{1000..1002}"      "#{1000..1003}"      "#{1000..1019}"      "#{1000.1099}"       "#{1000..1999}" ]
  [ "&darr; 1st arg &darr;" ]
  [ "#{}"            [[] []] ]
  [ "#{0..2}"        nil     [r0-3    r0-3] nil            nil             nil              nil               [r0-3    r1000-1003] ]
  [ "#{0..3}"        nil     [r0-4    r0-3] [r0-4    r0-4] nil             nil              nil               [r0-4    r1000-1003] [r0-4    r1000-1004] ]
  [ "#{0..19}"       nil     [r0-20   r0-3] [r0-20   r0-4] [r0-20   r0-20] nil              nil               [r0-20   r1000-1003] [r0-20   r1000-1004] [r0-20   r1000-1020] ]
  [ "#{0..99}"       nil     [r0-100  r0-3] [r0-100  r0-4] [r0-100  r0-20] [r0-100  r0-100] nil               [r0-100  r1000-1003] [r0-100  r1000-1004] [r0-100  r1000-1020] [r0-100  r1000-1100] ]
  [ "#{0..999}"      nil     [r0-1000 r0-3] [r0-1000 r0-4] [r0-1000 r0-20] [r0-1000 r0-100] [r0-1000 r0-1000] [r0-1000 r1000-1003] [r0-1000 r1000-1004] [r0-1000 r1000-1020] [r0-1000 r1000-1100] [r0-1000 r1000-2000] ]
  ])

(deftest ^:benchmark benchmark-union-funjible.set-vs-clojure.set
  (iprintf *err* "\nfunjible.set/union vs. cojure.set/union\n")
  (doseq [[f desc] set-fn-and-descs
          [coll1 coll2] (args-from-table-form union-results-table-form)]
    (let [s1 (f coll1), s2 (f coll2)]
;      (println (format "union %s %s" s1 s2))
      (benchmark {:fn "clojure.set/union" :set-type desc :args [s1 s2]}
                 [] (cset/union s1 s2))
      (benchmark {:fn "funjible.set-trans/union" :set-type desc :args [s1 s2]}
                 [] (fset-trans/union s1 s2))
      )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Benchmarks for: intersection
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def intersection-results-table-form [
  [ nil         "#{}"   "#{0..1}"      "#{0..2}"      "#{0..3}"      "#{0..19}"      "#{0..99}"       "#{0..999}"       "#{1000..1001}"      "#{1000..1002}"      "#{1000..1003}"      "#{1000..1019}"      "#{1000.1099}"       "#{1000..1999}" ]
  [ "#{}"       [[] []] ]
  [ "#{0..1}"   nil     [r0-2    r0-2] [r0-2    r0-3] [r0-2    r0-4] [r0-2    r0-20] [r0-2    r0-100] [r0-2    r0-1000] [r0-2    r1000-1002] [r0-2    r1000-1003] [r0-2    r1000-1004] [r0-2    r1000-1020] [r0-2    r1000-1100] [r0-2    r1000-2000] ]
  [ "#{0..2}"   nil     nil            [r0-3    r0-3] [r0-3    r0-4] [r0-3    r0-20] [r0-3    r0-100] [r0-3    r0-1000] nil                  [r0-3    r1000-1003] [r0-3    r1000-1004] [r0-3    r1000-1020] [r0-3    r1000-1100] [r0-3    r1000-2000] ]
  [ "#{0..3}"   nil     nil            nil            [r0-4    r0-4] [r0-4    r0-20] [r0-4    r0-100] [r0-4    r0-1000] nil                  nil                  [r0-4    r1000-1004] [r0-4    r1000-1020] [r0-4    r1000-1100] [r0-4    r1000-2000] ]
  [ "#{0..19}"  nil     nil            nil            nil            [r0-20   r0-20] [r0-20   r0-100] [r0-20   r0-1000] nil                  nil                  nil                  [r0-20   r1000-1020] [r0-20   r1000-1100] [r0-20   r1000-2000] ]
  [ "#{0..99}"  nil     nil            nil            nil            nil             [r0-100  r0-100] [r0-100  r0-1000] nil                  nil                  nil                  nil                  [r0-100  r1000-1100] [r0-100  r1000-2000] ]
  [ "#{0..999}" nil     nil            nil            nil            nil             nil              [r0-1000 r0-1000] nil                  nil                  nil                  nil                  nil                  [r0-1000 r1000-2000] ]
  ])

;; TBD: Consider adding [r0-1000 r0-2000] and [r0-1000 r1000-3000] to the above table.

(deftest ^:benchmark benchmark-intersection-funjible.set-vs-clojure.set
  (iprintf *err* "\nfunjible.set/intersection vs. cojure.set/intersection\n")
  (doseq [[f desc] set-fn-and-descs
          [coll1 coll2] (args-from-table-form intersection-results-table-form)]
    (let [s1 (f coll1), s2 (f coll2)]
;      (println (format "intersection %s %s" s1 s2))
      (benchmark {:fn "clojure.set/intersection" :set-type desc :args [s1 s2]}
                 [] (cset/intersection s1 s2))
      (benchmark {:fn "funjible.set-trans/intersection" :set-type desc :args [s1 s2]}
                 [] (fset-trans/intersection s1 s2))
      )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Benchmarks for: difference
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; difference on two sets is implemented differently depending upon
;; the relative sizes of the first and second set.

;; If the first set is smaller, it iterates over its elements,
;; checking whether each one is also in the second set, removing with
;; disj or disj! the elements that are in the second set.

;; If the first set is the same size or larger, it iterates over
;; elements of the second set, doing disj or disj! on the first set
;; for each such element.

;; Performance tests to hit each of these cases well should include
;; tests where:

;; + the first set is smaller, and disjoint from the second set (no
;; disj ops done).

;; + the first set is smaller, and a subset of the second set (lots of
;; disj ops that change the set).

;; + the first set is larger, and disjoint from second set (all disj
;; ops leave set unchanged).

;; + the first set is larger, and a superset of the the second set
;; (all disj ops change the set).

(def difference-results-table-form-needs-range-substitution [
  [ nil          nil "1st is disjoint and ..." nil                   "1st is subset of 2nd and ..." nil        nil "1st is disjoint and ..." nil               "1st is superset of 2nd and ..." ]
  [ nil          nil "one elem smaller"      "much smaller"          "one elem smaller" "much smaller"         nil "one elem larger"       "much larger"         "one elem larger"  "much larger" ]
  [ "&darr; 1st &darr;" ]
  [ "#{0..1}"    nil [[0    2] [1000 1003]]  [[0    2] [1000 4000]]  [[0    2] [0    3]]  [[0    2] [0 3000]]  nil [[0    2] [1000 1001]]  nil                     [[0    2] [0    1]]   ]
  [ "#{0..2}"    nil [[0    3] [1000 1004]]  [[0    3] [1000 4000]]  [[0    3] [0    4]]  [[0    3] [0 3000]]  nil [[0    3] [1000 1002]]  nil                     [[0    3] [0    2]]   ]
  [ "#{0..3}"    nil [[0    4] [1000 1005]]  [[0    4] [1000 4000]]  [[0    4] [0    5]]  [[0    4] [0 3000]]  nil [[0    4] [1000 1003]]  nil                     [[0    4] [0    3]]   ]
  [ "#{0..19}"   nil [[0   20] [1000 1021]]  [[0   20] [1000 4000]]  [[0   20] [0   21]]  [[0   20] [0 3000]]  nil [[0   20] [1000 1019]]  nil                     [[0   20] [0   19]]   ]
  [ "#{0..99}"   nil [[0  100] [1000 1101]]  [[0  100] [1000 4000]]  [[0  100] [0  101]]  [[0  100] [0 3000]]  nil [[0  100] [1000 1099]]  nil                     [[0  100] [0   99]]   ]
  [ "#{0..999}"  nil [[0 1000] [1000 2001]]  [[0 1000] [1000 4000]]  [[0 1000] [0 1001]]  [[0 1000] [0 3000]]  nil [[0 1000] [1000 1999]]  [[0 1000] [4000 4003]]  [[0 1000] [0  999]]  [[0 1000] [0    3]] ]
  [ "#{0..2999}" nil nil                     nil                     nil                  nil                  nil [[0 3000] [4000 6999]]  [[0 3000] [4000 4003]]  [[0 3000] [0 2999]]  [[0 3000] [0    3]] ]
  [ "#{0..2999}" nil nil                     nil                     nil                  nil                  nil nil                     [[0 3000] [4000 5000]]  nil                  [[0 3000] [0 1000]] ]
  ])

;; TBD: Add these arg pairs to table above?
;; [[] []]
;; 1st is a lot larger, disjoint          [(range 0 3000) (range 4000 5000)]
;; 1st is a lot larger, superset of 2nd   [(range 0 3000) (range 0 1000)]

(def difference-results-table-form
  (replace-range-specs-in-table-form
   difference-results-table-form-needs-range-substitution))


(deftest ^:benchmark benchmark-difference-funjible.set-vs-clojure.set
  (iprintf *err* "\nfunjible.set/difference vs. cojure.set/difference\n")
  (doseq [[f desc] set-fn-and-descs
          [coll1 coll2] (args-from-table-form difference-results-table-form)]
    (let [s1 (f coll1), s2 (f coll2)]
;      (println (format "difference %s %s" s1 s2))
      (benchmark {:fn "clojure.set/difference" :set-type desc :args [s1 s2]}
                 [] (cset/difference s1 s2))
      (benchmark {:fn "funjible.set-trans/difference" :set-type desc :args [s1 s2]}
                 [] (fset-trans/difference s1 s2))
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



(defn read-all [fname]
  (with-open [rdr (java.io.PushbackReader. (io/reader fname))]
    (doall (take-while #(not= % :eof)
                       (repeatedly #(edn/read {:eof :eof} rdr))))))


(defn first-consec-range-of-sorted-ints [s]
  (if-let [begin (first s)]
    (loop [s (rest s)
           last-seen begin]
      (if (seq s)
        (if (= (inc last-seen) (first s))
          (recur (rest s) (inc last-seen))
          [[begin last-seen] s])
        [[begin last-seen] nil]))))


(defn int-coll-to-ranges [c]
  (let [x (sort c)]
    (loop [ranges []
           x x]
      (if (seq x)
        (let [[next-range x-rest] (first-consec-range-of-sorted-ints x)]
          (recur (conj ranges (cons 'range next-range)) x-rest))
        ranges))))


(defn summarize-args [args]
  (map int-coll-to-ranges args))


(defn map-with-keys? [x ks]
  (and (map? x)
       (every? #(contains? x %) ks)))


(defn table-form? [x]
  (and (vector? x)
       (every? vector? x)))


(defn table-map? [x]
  (and (map? x)
       (every? (fn [k]
                 (and (vector? k)
                      (= 2 (count k))
                      (every? integer? k)))
               (keys x))))


;; Do (update-in [:args] summarize-args) on
;; benchmark-results-interesting-bits's result to shorten the large
;; sets with many consecutive integers.

(defn benchmark-results-interesting-bits [b]
  {:post [(map-with-keys? % [:fn :set-type :args :mean-time-sec])]}
  (let [d (:description b)
        [_ ns fn-name] (re-find #"^(.*)/(.*)$" (:fn d))]
    (assoc d
      :mean-time-sec (first (get-in b [:results :mean]))
      :namespace ns
      :general-fn fn-name)))


(defn table-form-to-table-map
  "Input tf is a 'table form', a vector of rows, where each row is a
vector of table cell contents.  Return a 'table map', which is a map
where the keys are vectors of the form [row col], and the
corresponding values are the contents of the table cell at that row
and col.  Row and col numbers begin at 0."
  [tf]
  {:pre [(table-form? tf)]
   :post [(table-map? %)]}
  (into {}
        (apply concat
               (map-indexed (fn [row-num row]
                              (map-indexed (fn [col-num table-entry]
                                             [[row-num col-num] table-entry])
                                           row))
                            tf))))


(defn print-table-map-as-html!
  [tm & opts]
  {:pre [(table-map? tm)]}
  (let [opts (apply hash-map opts)
        f (:fn-table-entry-to-html opts)
        num-rows (inc (apply max (map first (keys tm))))
        num-cols (inc (apply max (map second (keys tm))))]
    (print "<table style=\"text-align: right;\" border=\"1\" cellpadding=\"2\" cellspacing=\"2\">\n")
    (if (:caption opts)
      (printf "  <caption>%s</caption>\n" (:caption opts)))
    (print "  <tbody>\n")
    (dotimes [row num-rows]
      (print "    <tr>\n")
      (dotimes [col num-cols]
        (print "      <td>")
        (if-let [entry (get tm [row col])]
          (print (if f
                   (f entry row col)
                   (str entry))))
        (print "\n      </td>\n"))
      (print "    </tr>\n"))
    (print "  </tbody>\n")
    (print "</table>\n")))


;; Take a collection of benchmark results, a sequence of function
;; names, and a sequence of set types.  Returns only information about
;; those benchmarks results that are for one of the given function
;; names (key :fn in the benchmark result) and set types (key
;; :set-type).  Among the remaining benchmark results, group them by
;; the args they were called with, where the args are keys in a map.
;; The values in the map are themselves maps keyed by set type, and
;; the values in those maps are themselves maps keyed by function
;; name.

(defn args-to-benchmark-results-map
  [bench-results fn-names-seq set-type-seq]
  {:pre [(every? #(map-with-keys? % [:fn :set-type :args]) bench-results)]}
  (let [set-types (set set-type-seq)
        fn-names (set fn-names-seq)]
    (->> bench-results
         (filter #(and (fn-names (:fn %))
                       (set-types (:set-type %))))
         (reduce (fn [m bench-result]
                   (update-in m ((juxt :args :set-type :fn) bench-result)
                              conj bench-result))
                 {}))))


(defn time-to-html [t]
  (if t
    (format "%,.0f" (* t s-to-ns))
    "--"))


(defn time-change-html [t1 t2]
  (if (and t1 t2)
    (let [pct (* 100.0 (/ (- t2 t1) t1))]
      (format "<strong><mark>%.1f%%</mark></strong>" pct))
    "--"))


(defn one-arg-vec-to-html-str [arg-vec benchmark-results-map
                               fn-names-seq set-type-seq]
  (if-let [results (get benchmark-results-map arg-vec)]
    (let [lines (apply concat
                       (for [set-type set-type-seq]
                         (if-let [type-results (get results set-type)]
                           (let [[first-time & other-times]
                                 (map (fn [fn-name]
                                        (if-let [r (type-results fn-name)]
                                          (:mean-time-sec (first r))))
                                      fn-names-seq)]
                             (cons (time-to-html first-time)
                                   (mapcat (fn [t]
                                             [(time-to-html t)
                                              (time-change-html first-time t)])
                                           other-times)))
                           ;; else
                           [])))]
      (str/join "<br>" lines))))


(defn print-html-results-with-table-form! [benchmark-results
                                           table-form fn-name-seq set-type-seq]
  (let [table-map (table-form-to-table-map table-form)
        results-map (args-to-benchmark-results-map benchmark-results
                                                   fn-name-seq set-type-seq)]
    (print-table-map-as-html! table-map
                              :fn-table-entry-to-html
                              (fn [tm-entry row col]
                                (cond (nil? tm-entry) "nil"
                                      (string? tm-entry) tm-entry
                                      (vector? tm-entry) (one-arg-vec-to-html-str tm-entry
                                                                                  results-map
                                                                                  fn-name-seq
                                                                                  set-type-seq)
                                      :else tm-entry))
                              :caption
                              (format "%s<br>type %s"
                                      (str/join " vs. " fn-name-seq)
                                      (str/join ", " set-type-seq)))))


(defn print-all-benchmark-results! [benchmark-results]
  (doseq [[fn-name table-form] [["union" union-results-table-form]
                                ["intersection" intersection-results-table-form]
                                ["difference" difference-results-table-form]]
          set-type (map second set-fn-and-descs)]
    (let [full-fn-names [(str "clojure.set/" fn-name)
                         (str "funjible.set-trans/" fn-name)]]
      (print "<hr>\n")
      (print-html-results-with-table-form! benchmark-results table-form
                                           full-fn-names [ set-type ]))))


(defn get-bench [fname]
  (->> (read-all fname)
       (map benchmark-results-interesting-bits)))


(defn print-tables! [benchmark-results fname]
  (with-open [wrtr (io/writer fname)]
    (binding [*out* wrtr]
      (print-all-benchmark-results! benchmark-results))))



(deftest ^:bench-report benchmark-report
  (println "===== generating benchmark report =====")
  (let [x (get-bench "doc/2007-macpro/bench-2.txt")]
    (print-tables! x "doc/2007-macpro/bench-2.html")))
