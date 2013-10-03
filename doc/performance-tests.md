# Performance results

when using the following hardware and software:

* 2011 model MacBook Pro with 2 GHz Intel Core i7
* Mac OS X 10.8.5
* Apple/Oracle Java 1.7.0_15
* Leiningen 2.3.2 - Without the following line in my project.clj,
    criterium gave a helpful warning that TieredStopAtLevel=1 was
    enabled.  I added the line to project.clj and that warning went
    away.
```
    :jvm-opts ^:replace ["-server"]
```
* criterium 0.4.2
* funjible.set 0.0.1 - only changes from clojure.core/set are
  preconditions for args having the proper type of set or map, and doc
  strings.


## Comparison of mean execution times

Run with criterium.core/bench (60 sec runs):

```
clojure.set/union vs. funjible.set/union with args:
    #{} #{}              funjible.set 3.3% slower with avg  36.5 ns
    #{1 2 3} #{4 5}      funjible.set 0.8% slower with avg 484.8 ns

funjible.set/subset? vs. cojure.set/subset? with args:
    #{} #{}              funjible.set 1.4% slower with avg 147.3 ns
    #{1 2 3} #{4 5}      funjible.set 4.0% slower with avg  17.8 ns
    #{1 2 3} #{1 2 3 4}  funjible.set 1.7% slower with avg 701.2 ns
```


## Output from criterium

```
funjible.set/union vs. cojure.set/union

(funjible.set/union #{} #{} )
Evaluation count : 1546763160 in 60 samples of 25779386 calls.
             Execution time mean : 36.531832 ns
    Execution time std-deviation : 0.269900 ns
   Execution time lower quantile : 36.181086 ns ( 2.5%)
   Execution time upper quantile : 37.118347 ns (97.5%)
                   Overhead used : 2.532520 ns

Found 3 outliers in 60 samples (5.0000 %)
        low-severe       2 (3.3333 %)
        low-mild         1 (1.6667 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers

(clojure.set/union #{} #{} )
Evaluation count : 1587438060 in 60 samples of 26457301 calls.
             Execution time mean : 35.339746 ns
    Execution time std-deviation : 0.223502 ns
   Execution time lower quantile : 35.056575 ns ( 2.5%)
   Execution time upper quantile : 35.769648 ns (97.5%)
                   Overhead used : 2.532520 ns

Found 1 outliers in 60 samples (1.6667 %)
        low-severe       1 (1.6667 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


(funjible.set/union #{1 2 3} #{4 5} )
Evaluation count : 123674520 in 60 samples of 2061242 calls.
             Execution time mean : 484.841686 ns
    Execution time std-deviation : 3.594306 ns
   Execution time lower quantile : 478.709372 ns ( 2.5%)
   Execution time upper quantile : 491.482072 ns (97.5%)
                   Overhead used : 2.532520 ns

(clojure.set/union #{1 2 3} #{4 5} )
Evaluation count : 125076060 in 60 samples of 2084601 calls.
             Execution time mean : 481.076606 ns
    Execution time std-deviation : 4.783881 ns
   Execution time lower quantile : 473.639659 ns ( 2.5%)
   Execution time upper quantile : 491.908707 ns (97.5%)
                   Overhead used : 2.532520 ns

Found 2 outliers in 60 samples (3.3333 %)
        low-severe       2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


funjible.set/subset? vs. cojure.set/subset?

(funjible.set/subset? #{} #{} )
WARNING: Final GC required 1.0049625265648061 % of runtime
Evaluation count : 406213380 in 60 samples of 6770223 calls.
             Execution time mean : 147.301974 ns
    Execution time std-deviation : 1.932159 ns
   Execution time lower quantile : 143.995904 ns ( 2.5%)
   Execution time upper quantile : 150.922190 ns (97.5%)
                   Overhead used : 2.421873 ns
(clojure.set/subset? #{} #{} )
Evaluation count : 412692780 in 60 samples of 6878213 calls.
             Execution time mean : 145.280593 ns
    Execution time std-deviation : 1.809232 ns
   Execution time lower quantile : 142.668138 ns ( 2.5%)
   Execution time upper quantile : 150.014360 ns (97.5%)
                   Overhead used : 2.421873 ns

Found 3 outliers in 60 samples (5.0000 %)
        low-severe       2 (3.3333 %)
        low-mild         1 (1.6667 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers

(funjible.set/subset? #{1 2 3} #{4 5} )
Evaluation count : 3004328820 in 60 samples of 50072147 calls.
             Execution time mean : 17.766628 ns
    Execution time std-deviation : 0.182650 ns
   Execution time lower quantile : 17.502238 ns ( 2.5%)
   Execution time upper quantile : 18.188400 ns (97.5%)
                   Overhead used : 2.421873 ns

Found 2 outliers in 60 samples (3.3333 %)
        low-severe       2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
(clojure.set/subset? #{1 2 3} #{4 5} )
Evaluation count : 3110588460 in 60 samples of 51843141 calls.
             Execution time mean : 17.091368 ns
    Execution time std-deviation : 0.161881 ns
   Execution time lower quantile : 16.850995 ns ( 2.5%)
   Execution time upper quantile : 17.430647 ns (97.5%)
                   Overhead used : 2.421873 ns

Found 2 outliers in 60 samples (3.3333 %)
        low-severe       2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers

(funjible.set/subset? #{1 2 3} #{1 2 3 4} )
Evaluation count : 85110600 in 60 samples of 1418510 calls.
             Execution time mean : 701.247624 ns
    Execution time std-deviation : 5.272462 ns
   Execution time lower quantile : 690.637042 ns ( 2.5%)
   Execution time upper quantile : 711.826318 ns (97.5%)
                   Overhead used : 2.421873 ns

Found 2 outliers in 60 samples (3.3333 %)
        low-severe       1 (1.6667 %)
        low-mild         1 (1.6667 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
(clojure.set/subset? #{1 2 3} #{1 2 3 4} )
Evaluation count : 86920620 in 60 samples of 1448677 calls.
             Execution time mean : 689.586111 ns
    Execution time std-deviation : 6.949624 ns
   Execution time lower quantile : 680.351444 ns ( 2.5%)
   Execution time upper quantile : 704.971321 ns (97.5%)
                   Overhead used : 2.421873 ns

Found 2 outliers in 60 samples (3.3333 %)
        low-severe       2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
```
