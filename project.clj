(defproject funjible "1.0.0"
  :description "Almost, but not quite, exactly like Clojure core libraries"
  :url "http://github.com/jafingerhut/funjible"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:dev {:dependencies [[org.clojure/data.avl "0.0.17"]
                                  [org.clojure/data.int-map "0.2.4"]
                                  [org.clojure/data.priority-map "0.0.10"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}}

  ;; The definitions the funjible.set namespace are nearly identical
  ;; to the versions in the clojure.set namespace starting with
  ;; Clojure 1.5.1, and it has not changed from then until Clojure
  ;; 1.9.0 except for a small fix to a private function's doc string.

  ;; The only reason the profile 1.5 is not in the test-all alias
  ;; below is because org.clojure/data.int-map requires Clojure 1.6.0
  ;; or later.

  :aliases {"test-all" ["with-profile" "test,1.6,dev:test,1.7,dev:test,1.8,dev:test,1.9,dev" "test"]})
