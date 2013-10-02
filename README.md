# funjible

A Clojure library designed to be nearly identical to some core Clojure
libraries, but with more extensive error-checking and/or
documentation.

* funjible.set is like clojure.set


## Usage

TBD: Add Leiningen dependency line to add, after I know what that will
be.

TBD: Add examples in doc strings.

TBD: Add tests that verify these functions work with Michal's new
ordered set implementations?

TBD: List other set implementations that should work with these
functions (as well as clojure.set's).

* Zach Tellman's immutable bitsets use less memory when you only want
  sets of integers, especially if those integers have values close
  together.  https://github.com/ztellman/immutable-bitset

* TBD: Michale Marczyk's ordered sets.


## Performance notes

TBD: Measure performance overhead for :pre preconditions as the only
change.

TBD: Later investigate whether use of transients would speed things up
in functions like union, intersection, difference, select, etc.  If
there was a code path that never created a transient object if the
return value was unchanged from input value, that might be good for
performance, but maybe not so good for code clarity.

If transients are used, remember to preserve metadata, preferably in
the same way that clojure.set does.


## License

Copyright © 2013 Rich Hickey, Andy Fingerhut

Distributed under the Eclipse Public License version 1.0.
