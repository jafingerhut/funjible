(defproject funjible "0.1.0-SNAPSHOT"
  :description "Almost, but not quite, exactly like Clojure core libraries"
  :url "http://github.com/jafingerhut/funjible"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[avl.clj "0.0.8"]
                                  [immutable-bitset "0.1.4"]
                                  [org.clojure/data.priority-map "0.0.2"]
                                  [org.flatland/useful "0.10.3"]
                                  [org.clojure/tools.trace "0.7.6"]
                                  [criterium "0.4.2"]]}}
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :jvm-opts ^:replace ["-server"])
