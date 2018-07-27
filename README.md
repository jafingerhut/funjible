# funjible

A Clojure library designed to be nearly identical to some core Clojure
libraries, but with more extensive error-checking and/or
documentation.

* `funjible.set` is functionally identical to `clojure.set`, except
  its functions throw exceptions if you give them arguments of the
  wrong type.


## Releases and Dependency Information

Latest stable release: 0.1.0

* [All Released Versions](https://clojars.org/funjible/versions)

[Leiningen](https://github.com/technomancy/leiningen) dependency information:

```clojure
[funjible "0.1.0"]
```
[Maven](http://maven.apache.org/) dependency information:

```xml
<dependency>
  <groupId>funjible</groupId>
  <artifactId>funjible</artifactId>
  <version>0.1.0</version>
</dependency>
```


## Usage

An example 
```clojure
user=> (require '[funjible.set :as set])
nil

user=> (set/difference #{4 5} #{4 5 6})
#{}        ; as expected


user=> (clojure.set/difference #{4 5} [4 5 6])
#{4 5}   ; definitely violates the principle of least surprise

;; funjible.set throws an exception instead of quietly returning the
;; unexpected value.
user=> (set/difference #{4 5} [4 5 6])

AssertionError Assert failed: (set? s2)  funjible.set/difference (set.clj:60)

user=> (doc set/difference)
-------------------------
funjible.set/difference
([s1] [s1 s2] [s1 s2 & sets])
  Return a set that is the first set without elements of the
  remaining sets.  Throws exception if any argument is not a set.  The
  returned set will have the same metadata as s1, and will have the
  same 'sortedness', i.e. the returned set will be sorted if and only
  if s1 is.

  Example:
  user=> (difference #{2 4 6 8 10 12} #{3 6 9 12})
  #{2 4 8 10}
nil
```

Other Clojure set implementations:

* Zach Tellman's [immutable
  bitsets](https://github.com/clojure/data.int-map) use less
  memory when you only want sets of integers, especially if those
  integers have values close together.

* Michał Marczyk's [sorted sets and maps using AVL
  trees](https://github.com/clojure/data.avl) can efficiently
  find the rank of elements/keys, and they have transient
  implementations for them, unlike `clojure.core`'s sorted sets and
  maps.


## Performance notes

A few performance tests show that at least some of the funjible.set
functions are no more than 4% slower than their clojure.set
counterparts, and usually the performance penalty is less
percentage-wise than that.  The performance penalty in funjible.set
0.1.0 is purely due to the extra run-time type checking of arguments
using set?  and map?  See:

* https://github.com/jafingerhut/funjible/blob/master/doc/performance-tests.md


TBD: Investigate whether use of transients would speed things up in
functions like union, intersection, difference, select, etc.

It might help speed things up if there were paths through the
functions that never create a transient object if the return value is
unchanged from input value.  However, it may be not so good for code
clarity.

If transients are used, remember to preserve metadata in the return
values, in the same way that clojure.set does.


## Running benchmarks

```
% mkdir doc/2015-mbp
% lein test :benchmark > doc/2015-mbp/bench-1.txt

[ ... this will take about 12 hours to complete, during which it will
use about 1.2 CPU cores and 0.6 Gbytes of RAM, if you have that
available ... ]

[ Edit the file test/funjible/set_benchmark.clj, inside deftest
benchmark-report, to change the input file name to
doc/2015-mbp/bench-1.txt, and the output file name to something
similar, e.g. replace the "txt" suffix with "html".

You will also likely need to edit the contents of the file containing
the result data, e.g. doc/2015-mbp/bench-1.txt, to remove any line
like this near the beginning:

lein test funjible.set-benchmark

and any lines like this near the end:

Ran 1 tests containing 0 assertions.
0 failures, 0 errors.

If you forget to do this, the command below will likely fail with a
big stack trace.
]

% lein test :bench-report
```


## License

Copyright © 2013 Rich Hickey, Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
