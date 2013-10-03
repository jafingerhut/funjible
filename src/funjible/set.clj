;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Set operations such as union/intersection."
       :author "Rich Hickey"}
       funjible.set)

(defn- bubble-max-key [k coll]
  "Move a maximal element of coll according to fn k (which returns a number) 
   to the front of coll."
  (let [max (apply max-key k coll)]
    (cons max (remove #(identical? max %) coll))))

(defn union
  "Return a set that is the union of the input sets.  Throws exception
  if any argument is not a set.

  Example:
  user=> (union #{1 :a \"b\"} #{:a \"b\" -82 {:foo 7}})
  #{1 :a \"b\" {:foo 7} -82}"
  {:added "1.0"}
  ([] #{})
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2]
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s1) (count s2))
       (reduce conj s2 s1)
       (reduce conj s1 s2)))
  ([s1 s2 & sets]
     (let [bubbled-sets (bubble-max-key count (conj sets s2 s1))]
       (reduce into (first bubbled-sets) (rest bubbled-sets)))))

(defn intersection
  "Return a set that is the intersection of the input sets.  Throws
  exception if any argument is not a set.

  Example:
  user=> (intersection #{:k1 #{8/3 5} \"bar\"} #{:k1 \"goo\" #{8/3 5}})
  #{#{5 8/3} :k1}"
  {:added "1.0"}
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2]
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s2) (count s1))
       (recur s2 s1)
       (reduce (fn [result item]
                   (if (contains? s2 item)
		     result
                     (disj result item)))
	       s1 s1)))
  ([s1 s2 & sets] 
     (let [bubbled-sets (bubble-max-key #(- (count %)) (conj sets s2 s1))]
       (reduce intersection (first bubbled-sets) (rest bubbled-sets)))))

(defn difference
  "Return a set that is the first set without elements of the
  remaining sets.  Throws exception if any argument is not a set.

  Example:
  user=> (difference #{2 4 6 8 10 12} #{3 6 9 12})
  #{2 4 8 10}"
  {:added "1.0"}
  ([s1] {:pre [(set? s1)]} s1)
  ([s1 s2] 
     {:pre [(set? s1) (set? s2)]}
     (if (< (count s1) (count s2))
       (reduce (fn [result item] 
                   (if (contains? s2 item) 
                     (disj result item) 
                     result))
               s1 s1)
       (reduce disj s1 s2)))
  ([s1 s2 & sets] 
     (reduce difference s1 (conj sets s2))))


(defn select
  "Returns a set of the elements for which pred is true.  Throws
  exception if xset is not a set.

  Example:
  user=> (select even? #{3 6 9 12 15 18})
  #{6 12 18}"
  {:added "1.0"}
  [pred xset]
    {:pre [(set? xset)]}
    (reduce (fn [s k] (if (pred k) s (disj s k)))
            xset xset))

(defn project
  "Takes a relation (a set of maps) xrel, and returns a relation
  where every map contains only the keys in ks.  Throws exception
  if xrel is not a set.

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
  (with-meta (set (map #(select-keys % ks) xrel)) (meta xrel)))

(defn rename-keys
  "Returns the map with the keys in kmap renamed to the vals in kmap.
  Throws exception if any argument is not a map.

  Examples:
  user=> (rename-keys {:a 1 :b 2 :c 3} {:a :apple :b :bop})
  {:bop 2, :apple 1, :c 3}

  ;; It handles cases like this correctly, too.
  user=> (rename-keys {:a 1, :b 2, :c 3} {:a :b, :b :a})
  {:a 2, :b 1, :c 3}"
  {:added "1.0"}
  [map kmap]
    {:pre [(map? map) (map? kmap)]}
    (reduce 
     (fn [m [old new]]
       (if (contains? map old)
         (assoc m new (get map old))
         m)) 
     (apply dissoc map (keys kmap)) kmap))

(defn rename
  "Takes a relation (a set of maps) xrel, and returns a relation where
  all keys in kmap have been renamed to the corresponding vals in
  kmap.  Throws exception if xrel is not a set or kmap is not a map.

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
  (with-meta (set (map #(rename-keys % kmap) xrel)) (meta xrel)))

(defn index
  "Given a relation (a set of maps) xrel, return a map.  The keys are
  themselves maps of the distinct values of ks in xrel.  Each is
  mapped to the subset of xrel that has the corresponding values of
  ks.  Throws exception if xrel is not a set.

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
    (reduce
     (fn [m x]
       (let [ik (select-keys x ks)]
         (assoc m ik (conj (get m ik #{}) x))))
     {} xrel))

;; TBD: Consider changing map-invert return value to have metadata of
;; the arg m.
(defn map-invert
  "Returns the map with the vals mapped to the keys.  If a val appears
  more than once in the map, only one of its keys will appear in the
  result.  Do not rely on which one.  Throws exception if m is not a
  map.

  Examples:
  user=> (map-invert {:a 1, :b 2})
  {2 :b, 1 :a}
  user=> (map-invert {:a 1, :b 1})
  {1 :b}"
  {:added "1.0"}
  [m]
  {:pre [(map? m)]}
  (reduce (fn [m [k v]] (assoc m v k)) {} m))

(defn join
  "When passed 2 relations (sets of maps), returns the relation
  corresponding to the natural join.  When passed an additional
  keymap, joins on the corresponding keys.  Throws exception if xrel
  or yrel are not sets, or if km is not a map."
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
               #{} s))
     #{}))
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
             #{} s))))

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

