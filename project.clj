(defproject funjible "0.2.0-SNAPSHOT"
  :description "Almost, but not quite, exactly like Clojure core libraries"
  :url "http://github.com/jafingerhut/funjible"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :profiles {:dev {:dependencies [[org.clojure/data.avl "0.0.17"]
                                  [org.clojure/data.int-map "0.2.4"]
                                  [org.clojure/data.priority-map "0.0.10"]
                                  [org.flatland/useful "0.11.5"]
                                  [org.clojure/tools.trace "0.7.6"]
                                  [criterium "0.4.2"]]}}
  :test-selectors {:default (fn [m] (not (or (:benchmark m) (:bench-report m))))
                   :benchmark :benchmark
                   :bench-report :bench-report}
  :jvm-opts ^:replace ["-server" "-Xmx1024m"])
