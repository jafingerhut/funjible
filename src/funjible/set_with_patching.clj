(ns ^{:doc "Set operations such as union/intersection."
       :author "Rich Hickey"}
    funjible.set-with-patching
  (:require clojure.set
            [funjible.set :as fset]))


(defn- update-one-var-val-and-meta
  "Given a var orig-var and another new-var, replace the value of
  orig-var with that of new-var, using alter-var-root.  Afterwards,
  any calls made to the function that is the value of orig-var will
  behave the same as calls to new-var (unless they were compiled with
  direct linking enabled, in which case they will still use the
  original definition).

  Also replace the values of metadata keys :doc :file :line
  and :column of orig-var with the corresponding values of those keys
  from var new-var, using alter-meta!.  Afterwards, and any uses of
  clojure.repl/doc or clojure.repl/source on orig-var will see the new
  definition, not the original one."
  [orig-var new-var]
  (alter-var-root orig-var (constantly (deref new-var)))
  (alter-meta! orig-var merge (select-keys (meta new-var)
                                           [:doc :file :line :column])))

(defn- update-all-var-vals-and-meta
  []
  (update-one-var-val-and-meta #'clojure.set/union #'fset/union)
  (update-one-var-val-and-meta #'clojure.set/intersection #'fset/intersection)
  (update-one-var-val-and-meta #'clojure.set/difference #'fset/difference)
  (update-one-var-val-and-meta #'clojure.set/select #'fset/select)
  (update-one-var-val-and-meta #'clojure.set/project #'fset/project)
  (update-one-var-val-and-meta #'clojure.set/rename-keys #'fset/rename-keys)
  (update-one-var-val-and-meta #'clojure.set/rename #'fset/rename)
  (update-one-var-val-and-meta #'clojure.set/index #'fset/index)
  (update-one-var-val-and-meta #'clojure.set/map-invert #'fset/map-invert)
  (update-one-var-val-and-meta #'clojure.set/join #'fset/join)
  (update-one-var-val-and-meta #'clojure.set/subset? #'fset/subset?)
  (update-one-var-val-and-meta #'clojure.set/superset? #'fset/superset?))

(update-all-var-vals-and-meta)
