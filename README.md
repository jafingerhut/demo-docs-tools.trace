# demo-docs-tools.trace

I have programmed using Clojure as a hobby for several years, and knew
of tools.trace, but other than trying it out once or twice, never used
it much.  When using it for debugging an issue, I ran across some
confusing aspects of the included documentation, and also perhaps some
limitations of what the library can trace, vs. what it cannot, and I
wanted to make some notes about them for myself.


## Usage

See forms to evaluate in comment block at end of src/demo1/ns1.clj


## tools.trace and spec work by using `alter-var-root`

At least this is true for Clojure/JVM (Clojure running on the JVM).  I
haven't checked the details on ClojureScript.

### Cases where `alter-var-root` based tools work, vs. not

Here are Clojure tools that work by using `alter-var-root`:

+ `trace-ns` and `trace-vars` in the
  [`tools.trace`](https://github.com/clojure/tools.trace) library
+ `instrument` in Clojure spec

These tools, that might be in some ways similar to the above, do not
use `alter-var-root`:

+ `memo` and similar functions in the
  [`core.memoize`](https://github.com/clojure/core.memoize) library,
  as well as `memoize` built into Clojure.  These take a function as
  an argument and return a different function based on the one you
  give them.  They do not modify the behavior of any existing
  functions.

Here is a summary of where in Clojure/JVM based programs you can
expect tools based on `alter-var-root` to work, vs. where they will
not work.

+ They work on Clojure functions defined with `(defn foo ...)` or
  `(def foo (fn ...))`.
  + Exception: calls made in code compiled with [direct
    linking](https://clojure.org/reference/compilation#directlinking)
    enabled.  Starting with Clojure 1.8.0, all code in the core of
    Clojure is compiled with direct linking enabled, so calls within
    the core Clojure implementation to other functions within the core
    Clojure implementation are direct linked, and always use the
    original function definition.
  + Exception: calls where the compiler has inlined the function
    definition.
  + Exception: primitive type hinted functions (mentioned by Alex
    Miller - TBD an example of this to test?)
+ On a Clojure protocol method defined with `(defprotocol MyProtocol
  (my-method ...))`
  + But only if the definition of `my-method` is given using `extend`,
    `extend-type`, or `extend-protocol`.
  + Exception: If the definition of `my-method` is given inside of a
    `deftype` or `defrecord` form, it will not be affected.

In addition to the exceptions listed above, they do not work for these things:

+ Direct Java method calls using Clojure's Java interop syntax,
  e.g. `(.javaMethodName ...)` or `(java.contructorName. ...)`.

TBD: What about multimethods?


### Conditions where tools based on `alter-var-root` work

In Clojure/JVM, when you do `defn`, a Clojure Var is created, and its
value becomes the value of the function object created.  When you
compile your Clojure code with direct linking disabled (which is the
default setting), every time you call such a function created via
`defn` by its name, the compiled code generated for the call will look
up the current value of the Clojure Var with that name, and whatever
that value is, that is the function that is called.

The disadvantage of this level of indirection is a small additional
execution time on each function call.  The advantage is that it is
straightforward to use `defn` while a JVM is running, e.g. via a REPL
to that JVM, to change the value of a function.  Any future calls to
that function will use the new definition rather than the old one.

[Direct
linking](https://clojure.org/reference/compilation#directlinking) was
created as an option in the Clojure/JVM compiler to give another
choice.  The extra level of indirection is _not_ included where
functions are called.  The value of the Var is looked up at compile
time, and a reference to the compiled function is used directly in the
function call.  The advantage is a slightly faster function call.  The
disadvantage is that if you redefine the value of the Var, any calls
to that Var compiled with direct linking will continue using the old
definition, not the new one.

The `tools.trace` library can (in many cases) enable trace messages to
be printed when a function is called and when it returns, including
printing the values of arguments at call time and the return value at
return time.  The `tools.trace` library's `trace-vars` and `trace-ns`
functions work by modifying the value of Clojure Vars.  For example:

```clojure
user=> (require '[clojure.tools.trace :as t])
nil

user=> (defn f1 [x]
         (inc x))
#'user/f1

user=> (defn f2 [y]
         (- (f1 y) 7))
#'user/f2

user=> (f2 10)
4

user=> (t/trace-vars f1 f2)
#'user/f2

user=> (f2 10)
TRACE t1775: (user/f2 10)
TRACE t1776: | (user/f1 10)
TRACE t1776: | => 11
TRACE t1775: => 4
4

user=> (t/untrace-vars f1 f2)
#'user/f2
```

How does it do this?  When you call `trace-vars`, it calls a Clojure
core function
[`alter-var-root`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/alter-var-root)
([ClojureDocs.org
page](https://clojuredocs.org/clojure.core/alter-var-root)), which
modifies the value of a Var, to assign new values to the Vars.  The
new value assigned to the Var `#'user/f1` is a "wrapping" version of
the original function.  This "wrapping" function performs these steps:

+ Print a trace message showing the name `user/f1` and the values of
  the parameters.
+ Call the original function.
+ Print a trace message showing the name and the return value.
+ Return the value that the original function did.

Now any call to `user/f1` that performs the one level of indirection
through the Var `#'user/f1` will call this modified function instead
of the original.

Most of the work of `trace-vars` is done inside of a function called
[`trace-var*`](https://github.com/clojure/tools.trace/blob/908ddaf758f26e7ceba71543defd34849cc364af/src/main/clojure/clojure/tools/trace.clj#L313-L335).
You can see the call to `alter-var-root`
[here](https://github.com/clojure/tools.trace/blob/908ddaf758f26e7ceba71543defd34849cc364af/src/main/clojure/clojure/tools/trace.clj#L333-L334).

In order for `tools.trace`'s function `untrace-vars` to be able to
disable tracing on a function, `trace-vars` also remembers the value
of the original function in a place where `untrace-vars` can find it.
`trace-var*` stores the original function value in the metdata of the
Var with key `:clojure.tools.trace/traced`
[here](https://github.com/clojure/tools.trace/blob/908ddaf758f26e7ceba71543defd34849cc364af/src/main/clojure/clojure/tools/trace.clj#L335),
which is what `::traced` expands into when read by Clojure in the
namespace `clojure.tools.trace`.

The function `untrace-var*` in `tools.trace` uses `alter-var-root` to
restore the original value of the Var, and remove the saved function
from the Var's metadata.  You can see those steps
[here](https://github.com/clojure/tools.trace/blob/908ddaf758f26e7ceba71543defd34849cc364af/src/main/clojure/clojure/tools/trace.clj#L352-L353).

Clojure spec's `instrument` and `unstrument` functions work similarly.
[Here](https://github.com/clojure/spec.alpha/blob/f23ea614b3cb658cff0044a027cacdd76831edcf/src/main/clojure/clojure/spec/test/alpha.clj#L176)
is the part of `instrument` that replaces the original function with a
wrapping version that contains the additional instrumenting code, and
[here](https://github.com/clojure/spec.alpha/blob/f23ea614b3cb658cff0044a027cacdd76831edcf/src/main/clojure/clojure/spec/test/alpha.clj#L187)
is the part of `unstrument` that restores the Var back to its original
value.


## License

Copyright © 2018 Andy Fingerhut

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
