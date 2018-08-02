# funjible

A Clojure library designed to be nearly identical to some core Clojure
libraries, but with more extensive error-checking and/or
documentation.

Right now this includes only the namespace `funjible.set`.  This is
functionally identical to `clojure.set`, except its functions throw
exceptions if you give them arguments of the wrong type.

This library exists to give you the following choice:

(a) use the built-in `clojure.set` functions that, if you pass them
non-set collections, will quietly return results you may consider
wrong.

```clojure
user=> (require '[clojure.set :as set])
nil

user=> (set/difference #{4 5} #{4 5 6})
#{}       ; working as documented

user=> (set/difference #{4 5} [4 5 6])
#{4 5}    ; some people wish that this would throw an exception,
          ; or return #{}, but it doesn't.
```

(b) use the `funjible.set` versions, and get an exception if you pass
them arguments of a type that the function is not documented to
handle, at the expense of a little extra execution time to perform the
run-time type checks.

```clojure
user=> (require '[funjible.set :as set])
nil

user=> (set/difference #{4 5} #{4 5 6})
#{}       ; working as documented

user=> (set/difference #{4 5} [4 5 6])

AssertionError Assert failed: (set? s2)  funjible.set/difference (set.clj:88)
```


## Wait, isn't this what clojure.spec is for?

You most definitely can write simple specs for most or all of the
functions in the `clojure.set` namespace that, when instrumentation is
enabled, throw exceptions when they are passed arguments of
unsupported types, just as the modified versions in `funjible.set` do.

However, starting from the original versions in `clojure.set`, the
extra run time of the `funjible.set` versions is much smaller than for
the speced versions (see below).  Thus you may be willing to use the
`funjible.set` versions in long test runs, or even in production
deployments.

The table below gives run times as elapsed wall clock time, measured
using the [`criterium`](https://github.com/hugoduncan/criterium)
library on a 2015 MacBook Pro running OSX 10.12.6, Oracle JDK
1.8.0_181, and Clojure 1.9.0.  The specs used in these measurements
can be found
[here](https://github.com/jafingerhut/funjible-test-project/blob/master/src/funjible_test_project/set_speced.clj).

```clojure
;; The larger input values below are defined as:

(def s0-999 (set (range 0 1000)))
(def s1000-1999 (set (range 1000 2000)))
```

| Expression | `clojure.set` | `funjible.set` | `clojure.set` with spec instrumentation enabled |
| ---------- | ------------------- | -------------------- | ----------------------------------------------------- |
| `(union #{} #{})`                    | 54 nsec | 132 nsec | 8,768 nsec |
| `(union #{0 1 2} #{0 1 2})`          | 263 nsec | 280 nsec | 9,182 nsec |
| `(union #{0 1 2} #{1000 1001 1002})` | 498 nsec | 532 nsec | 9,330 nsec |
| `(union s0-999 #{0 1 2})`            | 285 nsec | 308 nsec | 9,107 nsec |
| `(union s0-999 s0-999)`              | 84,188 nsec | 86,447 nsec | 93,693 nsec |
| `(union s0-999 #{1000 1001 1002})`   | 769 nsec | 793 nsec | 9,589 nsec |
| `(union s0-999 s1000-1999)`          | 226,786 nsec | 225,932 nsec | 250,610 nsec |


## Releases and Dependency Information

Latest stable release: 1.0.0

* [All Released Versions](https://clojars.org/funjible/versions)

[Leiningen](https://github.com/technomancy/leiningen) /
[Boot](http://boot-clj.com) dependency information:

```clojure
[funjible "1.0.0"]
```

[Maven](http://maven.apache.org/) dependency information:

```xml
<dependency>
  <groupId>funjible</groupId>
  <artifactId>funjible</artifactId>
  <version>1.0.0</version>
</dependency>
```


## Usage

There are two primary ways to use the modified versions of
`clojure.set` functions in this library.

One is to modify as many of your Clojure namespaces as you wish that
currently require `clojure.set`, so that they instead require
`funjible.set`.  This gives you per-namespace control over which of
your code uses the new versions, vs. the originals, but requires
changing as many require clauses as you want to use the versions in
`funjible.set`.

The other is to pick at least one namespace anywhere in your code, and
require the namespace `funjible.set-with-patching`.  Requiring that
namespace not only loads the namespace `funjible.set`, it also
_modifies_ the functions in `clojure.set` to be the same as the
corresponding ones in `funjible.set`.  After requiring the
`funjible.set-with-patching` namespace, any call you make to
`clojure.set/union` will behave the same as a call to
`funjible.set/union`, with the extra argument type checking.  The only
exceptions to this are any calls to `clojure.set/union` that were
compiled with the Clojure compiler's [direct linking
option](https://clojure.org/reference/compilation#_compiler_options),
compiled before the `funjible.set-with-patching` namespace was
required.  Such calls will still call the original versions in
`clojure.set`.


An example in a project where you have _not_ required the namespace
`funjible.set-with-patching` anywhere:

```clojure
user=> (require '[funjible.set :as set])
nil

user=> (set/difference #{4 5} #{4 5 6})
#{}        ; as expected


user=> (clojure.set/difference #{4 5} [4 5 6])
#{4 5}   ; definitely surprises and dismays some people, hence this library

;; funjible.set throws an exception instead of quietly returning the
;; unexpected value.
user=> (set/difference #{4 5} [4 5 6])

AssertionError Assert failed: (set? s2)  funjible.set/difference (set.clj:88)

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

And here is the different behavior you get if _anywhere_ in your
project you have required `funjible.set-with-patching`.

```clojure
;; This is done in some namespace in your project, _not_ the one where
;; the other expressions below are evaluated:

(require 'funjible.set-with-patching)


;; The interaction below is in some namespace that never mentioned
;; `funjible.set` nor `funjible.set-with-patching`:

user=> (require 'clojure.set)
nil

user=> (clojure.set/difference #{4 5} [4 5 6])

AssertionError Assert failed: (set? s2)  funjible.set/difference (set.clj:88)


```


## Performance notes

A few performance tests show that at least some of the funjible.set
functions are no more than 4% slower than their clojure.set
counterparts, and usually the performance penalty is less
percentage-wise than that.  The performance penalty in funjible.set
1.0.0 is purely due to the extra run-time type checking of arguments
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


## Other Clojure set implementations

You can use `funjible.set` without using these other implementations
of sets, but note that the modified set operation functions in
`funjible.set` will work given instances of these other set types,
too, and any others not listed here, as long as those sets implement
normal Clojure primitive operations on sets such as `conj`, `disj`,
and `seq`.

* Zach Tellman's [immutable
  bitsets](https://github.com/clojure/data.int-map) use less
  memory when you only want sets of integers, especially if those
  integers have values close together.

* Michał Marczyk's [sorted sets and maps using AVL
  trees](https://github.com/clojure/data.avl) can efficiently
  find the rank of elements/keys, and they have transient
  implementations for them, unlike `clojure.core`'s sorted sets and
  maps.


## Running benchmarks

See the project
[`funjible-test-project`](https://github.com/jafingerhut/funjible-test-project).


## License

Copyright © 2013-2018 Rich Hickey, Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
