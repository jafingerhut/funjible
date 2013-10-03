# funjible

A Clojure library designed to be nearly identical to some core Clojure
libraries, but with more extensive error-checking and/or
documentation.

* `funjible.set` is functionally identical to `clojure.set`, except
  its functions throw exceptions if you give them arguments of the
  wrong type.


## Usage

TBD: Add Leiningen dependency line to add, after I know what that will
be.

Other Clojure set implementations:

* Zach Tellman's [immutable
  bitsets](https://github.com/ztellman/immutable-bitset) use less
  memory when you only want sets of integers, especially if those
  integers have values close together.

* Michał Marczyk's [sorted sets and maps using AVL
  trees](https://github.com/michalmarczyk/avl.clj) can efficiently
  find the rank of elements/keys, and they have transient
  implementations for them, unlike `clojure.core`'s sorted sets and
  maps.


## Performance notes

TBD: Measure performance overhead for :pre preconditions as the only
change.

TBD: Investigate whether use of transients would speed things up in
functions like union, intersection, difference, select, etc.

It might help speed things up if there were paths through the
functions that never create a transient object if the return value is
unchanged from input value.  However, it maybe not so good for code
clarity.

If transients are used, remember to preserve metadata in the return
values, in the same way that clojure.set does.


## License

Copyright © 2013 Rich Hickey, Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
