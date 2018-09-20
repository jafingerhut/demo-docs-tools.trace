# JVM implementation of normal Clojure function calls, and protocol function calls

## Disassembly of a normal function call, with one level of indirection through a Var

Here are two very simple Clojure functions.  Clojure on the JVM always
compiles all code to JVM byte code, and then if/when that code is JIT
compiled to native machine code is up to the JVM you are using.

```clojure
;; next line was line 96 of source file when no/disassemble was used
(defn x0 [y]
  (- y 1))

(defn x1 [y]
  (x0 y))
```

See the later section named "What defprotocol creates" for the Java
objects that evaluating a `defprotocol` form creates in Clojure/Java.

Below we show only the dissassembly of the function `x1`.  The only
reason I created function `x0` was so that in function `x1`, I could
call a Clojure Var that I knew was not inlined, had nothing to do with
direct linking being enabled, etc.  It would get the Var's current
value, then call that.

In the Clojure REPL transcript below, after copying and pasting it
here I hand-edited it to add the lines marked with `+ stack:` because
I am not very experienced with JVM byte code, and wanted to keep track
of the contents of the operand stack at each step.

See after the long disassembly listing for my notes on the core of the
implementation of function `x1`.

```clojure
user=> (require '[no.disassemble :as no])
user=> (require 'demo1.ns1)
user=> (println (no/disassemble demo1.ns1/x1))
// Compiled from ns1.clj (version 1.5 : 49.0, super bit)
public final class demo1.ns1$x1 extends clojure.lang.AFunction {
  
  // Field descriptor #13 Lclojure/lang/Var;
  public static final clojure.lang.Var const__0;
  
  // Method descriptor #7 ()V
  // Stack: 1, Locals: 1
  public ns1$x1();
    0  aload_0 [this]
    1  invokespecial clojure.lang.AFunction() [9]
    4  return
      Line numbers:
        [pc: 0, line: 99]
  
  // Method descriptor #11 (Ljava/lang/Object;)Ljava/lang/Object;
  // Stack: 3, Locals: 1
  public static java.lang.Object invokeStatic(java.lang.Object y);
     0  getstatic demo1.ns1$x1.const__0 : clojure.lang.Var [15]
      + stack: const__0 = #'demo1.ns1/x0
     3  invokevirtual clojure.lang.Var.getRawRoot() : java.lang.Object [21]
      + stack: (deref #'demo1.ns1/x0)
     6  checkcast clojure.lang.IFn [23]
      + stack: unchanged
     9  aload_0 [y]
      + stack: (deref #'demo1.ns1/x0) y
    10  aconst_null
      + stack: (deref #'demo1.ns1/x0) y null
    11  astore_0 [y]
      + stack: (deref #'demo1.ns1/x0) y
    12  invokeinterface clojure.lang.IFn.invoke(java.lang.Object) : java.lang.Object [26] [nargs: 2]
      + stack: return value of (x0 y)
    17  areturn
      Line numbers:
        [pc: 0, line: 99]
        [pc: 6, line: 100]
        [pc: 12, line: 100]
      Local variable table:
        [pc: 0, pc: 17] local: y index: 0 type: java.lang.Object
  
  // Method descriptor #11 (Ljava/lang/Object;)Ljava/lang/Object;
  // Stack: 2, Locals: 2
  public java.lang.Object invoke(java.lang.Object arg0);
    0  aload_1 [arg0]
    1  aconst_null
    2  astore_1 [arg0]
    3  invokestatic demo1.ns1$x1.invokeStatic(java.lang.Object) : java.lang.Object [30]
    6  areturn
      Line numbers:
        [pc: 3, line: 99]
  
  // Method descriptor #7 ()V
  // Stack: 2, Locals: 0
  public static {};
     0  ldc <String "demo1.ns1"> [33]
     2  ldc <String "x0"> [35]
     4  invokestatic clojure.lang.RT.var(java.lang.String, java.lang.String) : clojure.lang.Var [41]
     7  checkcast clojure.lang.Var [17]
    10  putstatic demo1.ns1$x1.const__0 : clojure.lang.Var [15]
    13  return
      Line numbers:
        [pc: 0, line: 99]

}
```

I believe that the code near the end, beginning with the line `public
static {};`, is static initialization code for the class created for
the function `x1`.  It is run only once when the class is loaded, and
initializes the static variable `const__0` to contain the value
`#'demo1.ns1/x0`, which is a reference to the Clojure Var, not the
value of the function object that you get when you deref that Var.

Before that, the biggest method is `invokeStatic`.  The lines below
are the heart of the function `x1`:

```clojure
     0  getstatic demo1.ns1$x1.const__0 : clojure.lang.Var [15]
     3  invokevirtual clojure.lang.Var.getRawRoot() : java.lang.Object [21]
     9  aload_0 [y]
    12  invokeinterface clojure.lang.IFn.invoke(java.lang.Object) : java.lang.Object [26] [nargs: 2]
    17  areturn
```

They call the method `clojure.lang.Var.getRawRoot()` to get the
current value of the Var `#'demo1.ns1/x0`, push `x1`'s parameter `y`
onto the stack, then call `invokeinterface` to cause the Clojure
function `x0` to be called, via calling the Java method
`clojure.lang.IFn.invoke`.

We will see this same sequence of JVM byte code instructions in the
disassembly for function `x2`.


## Disassembly of a protocol function call

Here is some Clojure code to define a simple protocol called
`Demo1Proto1` with two protocol functions `foo` and `bar-me`, where
`bar-me` has two arities, distinguished by the number of parameters
passed.

Later, the normal Clojure function `x2` makes a call to the protocol
function `bar-me`.

```clojure
(ns demo1.ns1)

(defprotocol Demo1Proto1
  (foo [this])
  (bar-me [this] [this y]))

;; ... later ...

(defn x2 [y]
  (bar-me y 32766))
```

Note that when Clojure evaluates a `defprotocol` form like the one
above, it has several side effects:

+ It creates two Clojure Vars whose values are Clojure functions, one
  `#'demo1.ns1/foo`, and one `#'demo1.ns1/bar-me`.  These functions
  will be called for the slow cases, where it is not easy to make the
  first parameter one that implements the Java interface described
  next.

+ It creates a Java interface, in this case called
  `demo1.ns1.Demo1Proto1`.  This interface has three method
  signatures, one for `foo`, and one for each of the two arities of
  `bar-me`.  The fast case for future calls to the protocol methods
  will be for objects that implement this Java interface.

Below is the disassembly of the function `x2`.  As in the previous
section, I hand-edited the REPL transcript below to add lines
beginning with `+`, for my own understanding of what was going on,
primarily with the current contents of the JVM stack.

Below that you can find my hand-translation of the body of `x2`'s
dissassembly into a Java-like pseudocode.  Read that to see how the
function `x2` has two cases to implement the calling of protocol
function `bar-me` -- the fast case when the first parameter implements
the Java interface, and the slow case where it calls the dereferenced
value of the Clojure Var `#'demo1.ns1/bar-me`.

```clojure
user=> (println (no/disassemble demo1.ns1/x2))
// Compiled from ns1.clj (version 1.5 : 49.0, super bit)
public final class demo1.ns1$x2 extends clojure.lang.AFunction {
  
  // Field descriptor #7 Ljava/lang/Class;
  private static java.lang.Class __cached_class__0;
  
  // Field descriptor #25 Lclojure/lang/Var;
  public static final clojure.lang.Var const__0;
  
  // Field descriptor #35 Ljava/lang/Object;
  public static final java.lang.Object const__1;
  
  // Method descriptor #9 ()V
  // Stack: 1, Locals: 1
  public ns1$x2();
    0  aload_0 [this]
    1  invokespecial clojure.lang.AFunction() [11]
    4  return
      Line numbers:
        [pc: 0, line: 99]
  
  // Method descriptor #13 (Ljava/lang/Object;)Ljava/lang/Object;
  // Stack: 3, Locals: 1
  public static java.lang.Object invokeStatic(java.lang.Object y);
     0  aload_0 [y]   // retrieve an object ref held in local variable 0 and push it onto the stack.  The "comment" [y] leads me to believe that this is the parameter y from the Clojure source code "(defn x2 [y] (bar-me y 32766))"
      + stack: y
     1  aconst_null  // pushes the special null object ref onto the stack
      + stack: y null
     2  astore_0 [y]  // pop object ref off stack and store it in local variable 0
      + stack: y
     3  dup   // duplicate top single-word item on the stack
      + stack: y y
     4  invokestatic clojure.lang.Util.classOf(java.lang.Object) : java.lang.Class [19]
      + stack: y classOf(y)
     7  getstatic demo1.ns1$x2.__cached_class__0 : java.lang.Class [21]  // pop the object ref from stack, retrieve value of the static field named by the first arg, push the one- or two-word value onto the stack
      + stack: y classOf(y) __cached_class__0
    10  if_acmpeq 27    // pop top 2 obj refs off stack and compares them.  If identical, jump to 27.  Otherwise continue with next instruction.
      + stack: y
    13  dup
      + stack: y y
    14  instanceof demo1.ns1.Demo1Proto1 [23]  // resolve demo1.ns1.Demo1Proto1 at run time.  Pop top object off stack, which should be object ref.  If it is an instance of the class or one of its subclasses, push 1 onto stack, else push 0.
      + stack: y ((y instanceof demo1.ns1.Demo1Proto1) ? 1 : 0)
    17  ifne 45  // pop top int off stack.  Branch to 45 if it is not 0, else fall through to next instruction.
      + stack: y
      + here we know that (y instanceof demo1.ns1.Demo1Proto) is false
    20  dup
      + stack: y y
    21  invokestatic clojure.lang.Util.classOf(java.lang.Object) : java.lang.Class [19]
      + stack: y classOf(y)
    24  putstatic demo1.ns1$x2.__cached_class__0 : java.lang.Class [21]
      + side effect: assign __cached_class__0 the value of classOf(y)
      + stack: y
    27  getstatic demo1.ns1$x2.const__0 : clojure.lang.Var [27]
      + stack: y (var demo1.ns1/bar-me)
    30  invokevirtual clojure.lang.Var.getRawRoot() : java.lang.Object [33]
      + stack: y (deref (var demo1.ns1/bar-me))
    33  swap
      + stack: (deref (var demo1.ns1/bar-me)) y
    34  getstatic demo1.ns1$x2.const__1 : java.lang.Object [37]
      + stack: (deref (var demo1.ns1/bar-me)) y Long.valueOf(32766)
    37  invokeinterface clojure.lang.IFn.invoke(java.lang.Object, java.lang.Object) : java.lang.Object [43] [nargs: 3]
      + stack: (ret value from calling (bar-me y 32766))
    42  goto 56
      + conditionally jump here from pc 17 instruction "ifne 45"
      + here we know that (y instanceof demo1.ns1.Demo1Proto) is true
      + stack: y
    45  checkcast demo1.ns1.Demo1Proto1 [23]
      + stack: y
    48  getstatic demo1.ns1$x2.const__1 : java.lang.Object [37]
      + stack: y Long.valueOf(32766)
    51  invokeinterface demo1.ns1.Demo1Proto1.bar_me(java.lang.Object) : java.lang.Object [46] [nargs: 2]
    56  areturn
      Line numbers:
        [pc: 0, line: 99]
        [pc: 0, line: 100]
        [pc: 37, line: 100]
      Local variable table:
        [pc: 0, pc: 56] local: y index: 0 type: java.lang.Object
  
  // Method descriptor #13 (Ljava/lang/Object;)Ljava/lang/Object;
  // Stack: 2, Locals: 2
  public java.lang.Object invoke(java.lang.Object arg0);
    0  aload_1 [arg0]
    1  aconst_null
    2  astore_1 [arg0]
    3  invokestatic demo1.ns1$x2.invokeStatic(java.lang.Object) : java.lang.Object [49]
    6  areturn
      Line numbers:
        [pc: 3, line: 99]

  // Method descriptor #9 ()V
  // Stack: 2, Locals: 0
  public static {};
     0  ldc <String "demo1.ns1"> [52]
     2  ldc <String "bar-me"> [54]
     4  invokestatic clojure.lang.RT.var(java.lang.String, java.lang.String) : clojure.lang.Var [60]
     7  checkcast clojure.lang.Var [29]
    10  putstatic demo1.ns1$x2.const__0 : clojure.lang.Var [27]
      + demo1.ns1$x2.const__0 = (var demo1.ns1/bar-me)
    13  ldc2_w <Long 32766> [61]
    16  invokestatic java.lang.Long.valueOf(long) : java.lang.Long [68]
    19  putstatic demo1.ns1$x2.const__1 : java.lang.Object [37]
      + demo1.ns1$x2.const__1 = Long.valueOf(32766)
    22  return
      Line numbers:
        [pc: 0, line: 99]

}
```

Below is the first version of my by-hand translation into Java
pseudocode of the `invokeStatic` method above.  It is written with
statements in the same order as the JVM byte codes appear, and
imagining that Java had a `goto` statement that worked something like
C's `goto`, with a named label as a target.  Most lines are commented
with a range of `pc` (program counter) values of the original JVM byte
code, which are the numbers at the beginning of many of the
disassembly lines.

```java
public final class demo1.ns1$x2 extends clojure.lang.AFunction {
  private static Class cached_class;
  
  public static Object invokeStatic (Object y) {
    y = null;   // pc 1-2
    if (clojure.lang.Util.classOf(y) == cached_class) {  // pc 3-10
        goto label_pc_27;
    }
    if (y instanceof demo1.ns1.Demo1Proto1) {  // pc 13-17
        goto label_pc_45;
    }
    cached_class = clojure.lang.Util.classOf(y);   // pc 20-24
label_pc_27:
    call function (var demo1.ns1/bar-me).getRawRoot() with parameters: y 32766  // pc 27-37
    goto label_pc_56;
label_pc_45:
    assert (y instanceof demo1.ns1.Demo1Proto1);  // pc 45
    demo1.ns1.Demo1Proto1.bar_me(y, 32766);   // pc 48-51
label_pc_56:
    return;
  }
}
```

Below I rearrange things slightly so `goto` is not needed, but the
order of the statements is slightly different than the order of the
JVM byte codes.  The behavior is the same, although I have left out a
few minor details like the `assert` (JVM byte code `checkcast`) and
the `y = null` assignment at the beginning.

```java
public final class demo1.ns1$x2 extends clojure.lang.AFunction {
  private static Class cached_class;
  
  public static Object invokeStatic (Object y) {
    if (clojure.lang.Util.classOf(y) != cached_class) {  // pc 3-10
        if (y instanceof demo1.ns1.Demo1Proto1) {  // pc 13-17
            // the fast case
            return demo1.ns1.Demo1Proto1.bar_me(y, 32766);  // pc 45-51
        }
        cached_class = clojure.lang.Util.classOf(y);  pc 20-24
    }
    // pc 27-37
    // the slow case
    same Java code emitted to do a call on a normal Clojure fn that looks like:
    (demo1.ns1/bar-me y 32766)
    including calling getRawRoot() method on the Var (var demo1.ns1/bar-me)
  }
}
```


## What `defprotocol` creates

The REPL transcript below demonstrates that evaluating this form:

```clojure
(defprotocol Demo1Proto1
  (foo [this])
  (bar-me [this] [this y]))
```

causes these things to be created:

+ A Java interface with a name that is the Clojure namespace
  concatenated with the protocol name, `user.Demo1Proto1` in this
  example.  It is in the package that has the same name as the
  namespace, probably munged to replace characters allowed in Clojure
  symbols that are not allowed in Java package names.

+ A Clojure Var in the namespace where the form is evaluated, with the
  name of the protocol, `#'user/Demo1Proto1` in this example.  Its
  value is a Clojure map containing various information about the
  protocol, including method names and signatures, and a reference to
  the Java interface object of the previous item.

+ One Clojure Var per protocol function/method.  The metadata of each
  of these Vars contains a reference to the Clojure Var of the
  previous item under the key `:protocol`.


```clojure
user=> Demo1Proto1

CompilerException java.lang.RuntimeException: Unable to resolve symbol: Demo1Proto1 in this context, compiling:(/private/var/folders/2j/n4d7hmm52zx8l6mpsgqm8tmc0000gn/T/form-init8618819583162487794.clj:1:1053) 
user=> user.Demo1Proto1

CompilerException java.lang.ClassNotFoundException: user.Demo1Proto1, compiling:(/private/var/folders/2j/n4d7hmm52zx8l6mpsgqm8tmc0000gn/T/form-init8618819583162487794.clj:1:1053) 
user=> foo

CompilerException java.lang.RuntimeException: Unable to resolve symbol: foo in this context, compiling:(/private/var/folders/2j/n4d7hmm52zx8l6mpsgqm8tmc0000gn/T/form-init8618819583162487794.clj:1:1053) 
user=> bar-me

CompilerException java.lang.RuntimeException: Unable to resolve symbol: bar-me in this context, compiling:(/private/var/folders/2j/n4d7hmm52zx8l6mpsgqm8tmc0000gn/T/form-init8618819583162487794.clj:1:1053) 
user=> (defprotocol Demo1Proto1
         (foo [this])
         (bar-me [this] [this y]))
Demo1Proto1

user=> Demo1Proto1
{:on user.Demo1Proto1, :on-interface user.Demo1Proto1, :sigs {:foo {:name foo, :arglists ([this]), :doc nil}, :bar-me {:name bar-me, :arglists ([this] [this y]), :doc nil}}, :var #'user/Demo1Proto1, :method-map {:bar-me :bar-me, :foo :foo}, :method-builders {#'user/bar-me #object[user$eval1586$fn__1587 0x15dba2 "user$eval1586$fn__1587@15dba2"], #'user/foo #object[user$eval1586$fn__1604 0x278976f5 "user$eval1586$fn__1604@278976f5"]}}

user=> (class Demo1Proto1)
clojure.lang.PersistentArrayMap

user=> #'Demo1Proto1
#'user/Demo1Proto1

user=> (meta #'Demo1Proto1)
{:line 1, :column 1, :file "/private/var/folders/2j/n4d7hmm52zx8l6mpsgqm8tmc0000gn/T/form-init8618819583162487794.clj", :name Demo1Proto1, :ns #object[clojure.lang.Namespace 0x19b3f73 "user"], :doc nil}

user=> (class user.Demo1Proto1)
java.lang.Class

user=> (identical? user.Demo1Proto1 (:on-interface Demo1Proto1))
true

user=> (require '[clojure.reflect :as ref])
nil

user=> (pprint (ref/reflect user.Demo1Proto1))
{:bases nil,
 :flags #{:interface :public :abstract},
 :members
 #{{:name bar_me,
    :return-type java.lang.Object,
    :declaring-class user.Demo1Proto1,
    :parameter-types [java.lang.Object],
    :exception-types [],
    :flags #{:public :abstract}}
   {:name foo,
    :return-type java.lang.Object,
    :declaring-class user.Demo1Proto1,
    :parameter-types [],
    :exception-types [],
    :flags #{:public :abstract}}
   {:name bar_me,
    :return-type java.lang.Object,
    :declaring-class user.Demo1Proto1,
    :parameter-types [],
    :exception-types [],
    :flags #{:public :abstract}}}}
nil

user=> foo
#object[user$eval1586$fn__1604$G__1575__1609 0x1132aac4 "user$eval1586$fn__1604$G__1575__1609@1132aac4"]

user=> bar-me
#object[user$eval1586$fn__1587$G__1577__1596 0x5fbb4e80 "user$eval1586$fn__1587$G__1577__1596@5fbb4e80"]

user=> (meta #'foo)
{:name foo, :arglists ([this]), :doc nil, :protocol #'user/Demo1Proto1, :ns #object[clojure.lang.Namespace 0x19b3f73 "user"]}

user=> (meta #'bar-me)
{:name bar-me, :arglists ([this] [this y]), :doc nil, :protocol #'user/Demo1Proto1, :ns #object[clojure.lang.Namespace 0x19b3f73 "user"]}
```
