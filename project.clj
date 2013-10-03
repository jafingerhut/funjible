(defproject funjible "0.0.1"
  :description "Almost, but not quite, exactly like Clojure core libraries"
  :url "http://github.com/jafingerhut/funjible"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[avl.clj "0.0.8"]
                                  [immutable-bitset "0.1.3"]
                                  [criterium "0.4.2"]]}}
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :jvm-opts ^:replace ["-server"])
