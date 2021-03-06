;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Set operations such as union/intersection."
       :author "Rich Hickey"}
       funjible.set-precondition-always-transient-mods)

;; This file is intended to have versions of the functions that use
;; transients whenever possible, even if the size of the collections
;; are very small and it might not give the best performance to do so.

;; Motivation: Measuring the performance of these functions against
;; the non-transient versions with different sizes of collections
;; should make it easier to see where the 'break even' point is,
;; i.e. what is the smallest collection size where the extra time to
;; convert to a transient and back to a persistent is still faster
;; because of the use of transients.


(defn- bubble-max-key
  "Move a maximal element of coll according to fn k (which returns a number) 
   to the front of coll."
  [k coll]
  (let [max (apply max-key k coll)]
    (cons max (remove #(identical? max %) coll))))

(defn- set-into [to from]
  (if (instance? clojure.lang.IEditableCollection to)
    (-> (reduce conj! (transient to) from)
        persistent!
        (with-meta (meta to)))
    (reduce conj to from)))

(defn union
  "Return a set that is the union of the input sets.  Throws exception
  if any argument is not a set.  The metadata and sortedness of the
  returned set is not promised, e.g. if you take the union of an
  unsorted and a sorted set, the returned set could be sorted or
  unsorted, and the metadata could be from either set.

  Example:
  user=> (union #{1 :a \"b\"} #{:a \"b\" -82 {:foo 7}})
  #{1 :a \"b\" {:foo 7} -82}"
  {:added "1.0"}
  ([] #{})
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2]
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s1) (count s2))
       (set-into s2 s1)
       (set-into s1 s2)))
  ([s1 s2 & sets]
     {:pre [(set? s1) (set? s2) (every? set? sets)]}
     (let [bubbled-sets (bubble-max-key count (conj sets s2 s1))]
       ;; TBD: The line below calls into N-1 times, so while it uses
       ;; transients internally when available in Clojure 1.5.1, it
       ;; will repeatedly change a set from transient, back to
       ;; persistent, back to transient, etc.  May be slightly faster
       ;; to only create one transient set and never convert it back
       ;; to persistent until the end.
       (reduce set-into (first bubbled-sets) (rest bubbled-sets)))))

(defn intersection
  "Return a set that is the intersection of the input sets.  Throws
  exception if any argument is not a set.  The metadata and sortedness
  of the returned set is not promised, e.g. if you take the
  intersection of an unsorted and a sorted set, the returned set could
  be sorted or unsorted, and the metadata could be from either set.

  Example:
  user=> (intersection #{:k1 #{8/3 5} \"bar\"} #{:k1 \"goo\" #{8/3 5}})
  #{#{5 8/3} :k1}"
  {:added "1.0"}
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2]
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s2) (count s1))
       (recur s2 s1)
       (if (instance? clojure.lang.IEditableCollection s1)
         (-> (reduce (fn [result item]
                       (if (contains? s2 item)
                         result
                         (disj! result item)))
                     (transient s1) s1)
             persistent!
             (with-meta (meta s1)))
         (reduce (fn [result item]
                   (if (contains? s2 item)
		     result
                     (disj result item)))
                 s1 s1))))
  ([s1 s2 & sets] 
     ;; Note: No need for preconditions here, because the precondition
     ;; in the 2-argument version will catch any non-set arguments to
     ;; this version.
     (let [bubbled-sets (bubble-max-key #(- (count %)) (conj sets s2 s1))]
       (reduce intersection (first bubbled-sets) (rest bubbled-sets)))))

(defn difference
  "Return a set that is the first set without elements of the
  remaining sets.  Throws exception if any argument is not a set.  The
  returned set will have the same metadata as s1, and will have the
  same 'sortedness', i.e. the returned set will be sorted if and only
  if s1 is.

  Example:
  user=> (difference #{2 4 6 8 10 12} #{3 6 9 12})
  #{2 4 8 10}"
  {:added "1.0"}
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2] 
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s1) (count s2))
       (if (instance? clojure.lang.IEditableCollection s1)
         (-> (reduce (fn [result item]
                       (if (contains? s2 item)
                         (disj! result item)
                         result))
                     (transient s1) s1)
             persistent!
             (with-meta (meta s1)))
         (reduce (fn [result item] 
                   (if (contains? s2 item) 
                     (disj result item) 
                     result))
                 s1 s1))
       (if (instance? clojure.lang.IEditableCollection s1)
         (-> (reduce disj! (transient s1) s2)
             persistent!
             (with-meta (meta s1)))
         (reduce disj s1 s2))))
  ([s1 s2 & sets] 
     ;; Note: No need for preconditions here, because the precondition
     ;; in the 2-argument version will catch any non-set arguments to
     ;; this version.
     (reduce difference s1 (conj sets s2))))


(defn select
  "Returns a set of the elements for which pred is true.  Throws
  exception if xset is not a set.  The metadata and 'sortedness' of
  the returned set will be the same as xset, i.e. it will be unsorted
  or sorted in the same way that xset is.

  Example:
  user=> (select even? #{3 6 9 12 15 18})
  #{6 12 18}"
  {:added "1.0"}
  [pred xset]
    {:pre [(set? xset)]}
    (if (instance? clojure.lang.IEditableCollection xset)
      (-> (reduce (fn [s k] (if (pred k) s (disj! s k)))
                  (transient xset) xset)
          persistent!
          (with-meta (meta xset)))
      (reduce (fn [s k] (if (pred k) s (disj s k)))
              xset xset)))

(defn project
  "Takes a relation (a set of maps) xrel, and returns a relation where
  every map contains only the keys in ks.  Throws exception if xrel is
  not a set.  The metadata and 'sortedness' of the returned set will
  be the same as xrel, i.e. it will be unsorted or sorted in the same
  way that xrel is.

  Example:
  user=> (def rel #{{:name \"Art of the Fugue\" :composer \"J. S. Bach\"}
                    {:name \"Musical Offering\" :composer \"J. S. Bach\"}
                    {:name \"Requiem\" :composer \"W. A. Mozart\"}})
  #'user/rel
  user=> (project rel [:composer])
  #{{:composer \"W. A. Mozart\"}
    {:composer \"J. S. Bach\"}}"
  {:added "1.0"}
  [xrel ks]
  {:pre [(set? xrel)]}
  (into (empty xrel) (map #(select-keys % ks) xrel)))

(defn rename-keys
  "Returns the map with the keys in kmap renamed to the vals in kmap.
  Throws exception if any argument is not a map.  The metadata and
  'sortedness' of the returned map will be the same as map, i.e. it
  will be unsorted or sorted in the same way that map is.

  Examples:
  user=> (rename-keys {:a 1 :b 2 :c 3} {:a :apple :b :bop})
  {:bop 2, :apple 1, :c 3}

  ;; It handles cases like this correctly, too.
  user=> (rename-keys {:a 1, :b 2, :c 3} {:a :b, :b :a})
  {:a 2, :b 1, :c 3}"
  {:added "1.0"}
  [map kmap]
    {:pre [(map? map) (map? kmap)]}
    (if (instance? clojure.lang.IEditableCollection map)
      (let [tmap (apply dissoc! (transient map) (keys kmap))]
        (-> (reduce (fn [m [old new]]
                      (if (contains? map old)
                        (assoc! m new (get map old))
                        m))
                    tmap kmap)
            persistent!
            (with-meta (meta map))))
      (reduce (fn [m [old new]]
                (if (contains? map old)
                  (assoc m new (get map old))
                  m)) 
              (apply dissoc map (keys kmap)) kmap)))

(defn rename
  "Takes a relation (a set of maps) xrel, and returns a relation where
  all keys of xrel that are in kmap have been renamed to the
  corresponding vals in kmap.  Throws exception if xrel is not a set
  or kmap is not a map.  The metadata and 'sortedness' of the returned
  set will be the same as xrel, i.e. it will be unsorted or sorted in
  the same way that xrel is.

  Example:
  user=> (def rel #{{:name \"Art of the Fugue\" :composer \"J. S. Bach\"}
                    {:name \"Musical Offering\" :composer \"J. S. Bach\"}
                    {:name \"Requiem\" :composer \"W. A. Mozart\"}})
  #'user/rel
  user=> (rename rel {:name :title})
  #{{:title \"Art of the Fugue\", :composer \"J. S. Bach\"}
    {:title \"Musical Offering\", :composer \"J. S. Bach\"}
    {:title \"Requiem\", :composer \"W. A. Mozart\"}}"
  {:added "1.0"}
  [xrel kmap]
  {:pre [(set? xrel) (map? kmap)]}
  (into (empty xrel) (map #(rename-keys % kmap) xrel)))


;; TBD: Update index to use transients, at least for the map returned,
;; and perhaps also for the sets that are the values in the map.

;; Note: If we use transients for the individual sets, there needs to
;; be a final pass over the whole outer map to convert them all to
;; persistents, and to add the metadata onto each of them.  Also, I
;; think empty-xrel would need to be re-transiented each time a new
;; map key/value pair is created.

(defn index
  "Given a relation (a set of maps) xrel, return a map.  The keys are
  themselves maps of the distinct values of ks in xrel.  Each is
  mapped to the subset of xrel that has the corresponding values of
  ks.  Throws exception if xrel is not a set.  Each subset of xrel
  returned has the same metadata and 'sortedness' as xrel, i.e. they
  will be unsorted or sorted in the same way that xrel is.

  user=> (def people #{{:name \"Lakshmi\", :age 8}
                       {:name \"Hans\", :age 9}
                       {:name \"Rahul\", :age 10}
                       {:name \"George\", :age 8}
                       {:name \"Paula\", :age 10}})
  #'user/people
  user=> (index people [:age])
  {{:age 8} #{{:age 8, :name \"Lakshmi\"}
              {:age 8, :name \"George\"}},
   {:age 10} #{{:age 10, :name \"Rahul\"}
               {:age 10, :name \"Paula\"}},
   {:age 9} #{{:age 9, :name \"Hans\"}}}"
  {:added "1.0"}
  [xrel ks]
    {:pre [(set? xrel)]}
    (let [empty-xrel (empty xrel)]
      (reduce
       (fn [m x]
         (let [ik (select-keys x ks)]
           (assoc m ik (conj (get m ik empty-xrel) x))))
       {} xrel)))

(defn map-invert
  "Returns the map with the vals mapped to the keys.  If a val appears
  more than once in the map, only one of its keys will appear in the
  result.  Do not rely on which one.  Throws exception if m is not a
  map.  The returned map has no metadata, and is not guaranteed to be
  any particular type of map (e.g. if m is a sorted map, there is no
  promise the returned map will be sorted).

  Examples:
  user=> (map-invert {:a 1, :b 2})
  {2 :b, 1 :a}
  user=> (map-invert {:a 1, :b 1})
  {1 :b}"
  {:added "1.0"}
  [m]
  {:pre [(map? m)]}
  (-> (reduce (fn [m [k v]] (assoc! m v k))
              (transient {}) m)
      persistent!))

;; TBD: Investigate how transients might be used to speed up join.

(defn join
  "When passed 2 relations (sets of maps), returns the relation
  corresponding to the natural join.  When passed an additional
  keymap, joins on the corresponding keys.  Throws exception if xrel
  or yrel are not sets, or if km is not a map.  The returned set will
  always have the same metadata and 'sortedness' as the first set
  given, i.e. the returned set will be unsorted or sorted in the same
  way that xrel is."
  {:added "1.0"}
  ([xrel yrel] ;natural join
   {:pre [(set? xrel) (set? yrel)]}
   (if (and (seq xrel) (seq yrel))
     (let [ks (intersection (set (keys (first xrel))) (set (keys (first yrel))))
           [r s] (if (<= (count xrel) (count yrel))
                   [xrel yrel]
                   [yrel xrel])
           idx (index r ks)]
       (reduce (fn [ret x]
                 (let [found (idx (select-keys x ks))]
                   (if found
                     (reduce #(conj %1 (merge %2 x)) ret found)
                     ret)))
               (empty xrel) s))
     (empty xrel)))
  ([xrel yrel km] ;arbitrary key mapping
   {:pre [(set? xrel) (set? yrel) (map? km)]}
   (let [[r s k] (if (<= (count xrel) (count yrel))
                   [xrel yrel (map-invert km)]
                   [yrel xrel km])
         idx (index r (vals k))]
     (reduce (fn [ret x]
               (let [found (idx (rename-keys (select-keys x (keys k)) k))]
                 (if found
                   (reduce #(conj %1 (merge %2 x)) ret found)
                   ret)))
             (empty xrel) s))))

(defn subset? 
  "Is set1 a subset of set2?  True if the sets are equal.  Throws
  exception if any argument is not a set.

  Examples:
  user=> (subset? #{\"two\" \"strings\"} #{\"strings\" \"two\" \"plus\"})
  true
  user=> (subset? #{3 4 5} #{3 4 7})
  false"
  {:added "1.2",
   :tag Boolean}
  [set1 set2]
  {:pre [(set? set1) (set? set2)]}
  (and (<= (count set1) (count set2))
       (every? #(contains? set2 %) set1)))

(defn superset? 
  "Is set1 a superset of set2?  True if the sets are equal.  Throws
  exception if any argument is not a set.

  Examples:
  user=> (superset? #{\"this\" \"has\" \"more\"} #{\"this\" \"has\"})
  true
  user=> (superset? #{2 3 5 7 11 13} #{9})
  false"
  {:added "1.2",
   :tag Boolean}
  [set1 set2]
  {:pre [(set? set1) (set? set2)]}
  (and (>= (count set1) (count set2))
       (every? #(contains? set1 %) set2)))

(comment
(refer 'set)
(def xs #{{:a 11 :b 1 :c 1 :d 4}
         {:a 2 :b 12 :c 2 :d 6}
         {:a 3 :b 3 :c 3 :d 8 :f 42}})

(def ys #{{:a 11 :b 11 :c 11 :e 5}
         {:a 12 :b 11 :c 12 :e 3}
         {:a 3 :b 3 :c 3 :e 7 }})

(join xs ys)
(join xs (rename ys {:b :yb :c :yc}) {:a :a})

(union #{:a :b :c} #{:c :d :e })
(difference #{:a :b :c} #{:c :d :e})
(intersection #{:a :b :c} #{:c :d :e})

(index ys [:b])
)

