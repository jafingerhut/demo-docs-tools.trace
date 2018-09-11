(ns demo1.ns1
  (:require [clojure.spec.alpha :as s]))


(defn bar
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defprotocol Demo1Proto1
  (foo [this])
  (bar-me [this] [this y]))

(s/fdef bar-me
  :args (s/cat :thisarg any? :yarg (s/? pos?))
  :ret any?)

(deftype Demo1Type [a b c]
  Demo1Proto1
  (foo [this] a)
  (bar-me [this] b)
  (bar-me [this y] (+ c y)))

(extend-type java.lang.Long
  Demo1Proto1
  (foo [this] (+ this 17))
  ;; Note: Even though deftype can define the two arities of bar-me as
  ;; separate expressions, this does not give a working multi-arity
  ;; implementation if you try that inside of extend-type.  It will
  ;; not give any error from compilation, but likely at least one of
  ;; the arities will throw an exception if you attempt to call it.
  (bar-me
    ([this] (* this 3))
    ([this y] (/ this y))))


(defprotocol Demo1Proto2
  (baz [this])
  (guh [this y]))

(extend-type java.lang.Long
  Demo1Proto2
  (baz [this] [:baz-on-long this])
  (guh [this y] {:guh-on-long this :y y}))


(deftype Demo1OtherType [w x])

(extend-type Demo1OtherType
  Demo1Proto1
  (foo [this] {:called :Demo1Proto1-foo :this this})
  ;; multi-arity function definitions must be combined inside of
  ;; extend-type.  Clojure compiler will not give any error if you
  ;; instead define them separately, but it will silently overwrite
  ;; the earlier definition with the later one.
  (bar-me
    ([this]
     {:called :Demo1Proto1-bar-me-arity-1 :this this})
    ([this y]
     {:called :Demo1Proto1-bar-me-arity-2 :this this :y y}))

  Demo1Proto2
  (baz [this] {:called :Demo1Proto2-baz :this this})
  (guh [this y] {:called :Demo1Proto2-guh :this this :y y}))


(defrecord Demo1Rec1 [a b]
  Demo1Proto1
  (foo [this] {:called :Demo1Proto1-foo :this this})
  ;; multi-arity function definitions must be separate inside of
  ;; defrecord.
  ;;(bar-me
  ;;  ([this] (* b 9))
  ;;  ([this y] (* a b y))
  (bar-me [this]
    {:called :Demo1Proto1-bar-me-arity-1 :this this})
  (bar-me [this y]
    {:called :Demo1Proto1-bar-me-arity-2 :this this :y y})
  )

(extend-type demo1.ns1.Demo1Rec1
  Demo1Proto2
  (baz [this]
    [:baz-on-demo1rec1 this :a (:a this)]
    ;; The following line gives compiler error "Unable to resolve
    ;; symbol: a in this context".  TBD: Is there a way to use
    ;; extend-type on a record and use the fields in the body, the
    ;; same way as when defining the methods inside of the defrecord
    ;; form itself?  If not, I guess the best way is just to use
    ;; syntax like (:a this) to get it?
    ;;[:baz-on-demo1rec1 this :a a]
    )
  (guh [this y] {:guh-on-demo1rec1 this :y y}))


(defn x0 [y]
  (- y 1))

(defn x1 [y]
  (x0 y))

(defn x2 [y]
  (bar-me y 32766))

(defn x7 [y]
  [(foo y) (bar-me y) (bar-me y -1)])


(comment

(require '[clojure.tools.trace :as t])
(require '[demo1.ns1 :as d] :reload)
(require '[clojure.spec.test.alpha :as stest])
(require '[no.disassemble :as no])

(println (no/disassemble (fn [])))

(defn java-cp []
  (get (System/getProperties) "java.class.path"))

(defn java-cp-split []
  (clojure.string/split (java-cp) #":"))

(t/trace-vars d/foo d/bar-me)
(t/untrace-vars d/foo d/bar-me)

;; Here are values you can pass to trace-ns / untrace-ns as parameters
;; with success:

(t/trace-ns 'demo1.ns1)  ;; full namespace name as quoted symbol
(t/trace-ns (the-ns 'demo1.ns1))  ;; The object with type clojure.lang.Namespace

(t/untrace-ns 'demo1.ns1)

;; Here are values you can pass to trace-ns / untrace-ns as parameters
;; _without_ success:

;; full namespace name as unquoted symbol.  In fact, _any_ reference
;; to something like demo1.ns1 or a.b, whether it is an existing
;; namespace or not, will fail with a CompilerException wrapped around
;; a ClassNotFoundException.
(t/trace-ns demo1.ns1)  ;; full namespace name as unquoted symbol
(t/trace-ns 'd) ;; namespace alias as symbol

(stest/instrument `d/bar-me)
(stest/unstrument `d/bar-me)

;; With Oracle JDK 8, Clojure 1.9.0, tools.trace 0.7.9, whether trace
;; messages appeared or not is in comments marked "v1".

;; v1: no trace messages for protocol method calls on Demo1Type
;; v1: no instrument checks spec of bar-me args
(def d1 (demo1.ns1.Demo1Type. 5 10 15))
(d/foo d1)
(d/bar-me d1)
(d/bar-me d1 -1)

;; v1: _yes_ trace messages for using apply.
;; v1: _yes_ instrument checks spec of bar-me args
(apply d/foo [d1])
(apply d/bar-me [d1])
(apply d/bar-me [d1 -1])

;; v1: no trace messages for calling d/x7 on d1.  Hmmm.
;; v1: no instrument checks spec of bar-me args
(d/x7 d1)

;; v1: yes trace message for unimplemented protocol method call on
;; Demo1Type, at least for the call, but not return, because exception
;; thrown before return.
(d/baz d1)
(d/guh d1 5)

;; v1: yes trace messages for protocol method calls on java.lang.Long
;; v1: _yes_ instrument checks spec of bar-me args
(d/foo 100)
(d/bar-me 100)
(d/bar-me 100 8)
(d/bar-me 100 -1)
(d/baz 100)
(d/guh 100 5)
(d/x7 100)

;; v1: yes trace messages for protocol method calls on Demo1OtherType,
;; where all of the implementations were given using extend-type.
;; v1: _yes_ instrument checks spec of bar-me args
(def d2 (demo1.ns1.Demo1OtherType. 31 63))
(d/foo d2)
(d/bar-me d2)
(d/bar-me d2 -1)
(d/baz d2)
(d/guh d2 5)
(d/x7 d2)


;; v1: yes trace message for record constructor functions ->Demo1Rec1
;; and map->Demo1Rec1
(def r1 (d/->Demo1Rec1 17 19))
(def r2 (d/map->Demo1Rec1 {:a "a" :b "b"}))

;; v1: no trace messages for protocol method calls on a record, where
;; the method implementations were given within the defrecord form.
;; v1: no instrument checks spec of bar-me args
(d/foo r1)
(d/bar-me r1)
(d/bar-me r1 23)
(d/bar-me r1 -1)
(d/x7 r1)

;; v1: yes trace messages for protocol method calls on a record, where
;; the method implementations were given within an extend-type form
;; after defrecord and defprotocol.
(d/baz r1)
(d/guh r1 37)

;; Evaluating d/bar-me before and after trace-vars below, and again
;; after untrace-vars.  It restores back to the original value after
;; untrace-vars.  That makes sense, since I believe that tools.trace
;; works by 'wrapping' the original function in another one that
;; prints extra things when tracing is enabled, and restoring the
;; original function when tracing is turned off.

(fn? d/bar-me)

;; tracing not enabled now, d/bar-me has its original value
d/bar-me
(d/bar-me 100 -1)
;; enable tracing
(t/trace-vars d/foo d/bar-me)
;; tracing enabled now, d/bar-me has different than original value
d/bar-me
(d/bar-me 100 -1)
;; disable tracing
(t/untrace-vars d/foo d/bar-me)
;; tracing disabled now, d/bar-me has been restored to its original value
d/bar-me
(d/bar-me 100 -1)

;; instrument not enabled now, d/bar-me has its original value
d/bar-me
(d/bar-me 100 -1)
;; enable instrument
(stest/instrument `d/bar-me)
;; instrument enabled now, d/bar-me has different than original value
d/bar-me
(d/bar-me 100 -1)
;; disable instrument
(stest/unstrument `d/bar-me)
;; instrument disabled now, d/bar-me has been restored to its original value
d/bar-me
(d/bar-me 100 -1)

;; What happens if we try to enable both tracing and instrument on
;; d/bar-me?

;; For disabling both of those features on a Var, does it only work
;; correctly if they are disabled in the opposite order that they were
;; enabled?

;; neither trace nor instrument enabled now, d/bar-me has its original value
d/bar-me
;; #object[demo1.ns1$eval1713$fn__1714$G__1704__1723 0x3d83ab4e "demo1.ns1$eval1713$fn__1714$G__1704__1723@3d83ab4e"]
(d/bar-me 100 -1)
;; enable instrument
(stest/instrument `d/bar-me)
d/bar-me
;; #object[clojure.spec.test.alpha$spec_checking_fn$fn__2943 0x648975fb "clojure.spec.test.alpha$spec_checking_fn$fn__2943@648975fb"]
(d/bar-me 100 -1)
;; instrument threw exception
;; enable trace while instrument is enabled
(t/trace-vars d/foo d/bar-me)
d/bar-me
;; #object[clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616 0x37aa6caa "clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616@37aa6caa"]
(d/bar-me 100 -1)
;; trace msg printed on call, but not on return, because instrument
;; threw exception before return.  Both clearly enabled on d/bar-me.
(t/untrace-vars d/foo d/bar-me)
d/bar-me
;; #object[clojure.spec.test.alpha$spec_checking_fn$fn__2943 0x648975fb "clojure.spec.test.alpha$spec_checking_fn$fn__2943@648975fb"]
(d/bar-me 100 -1)
;; instrument threw exception, but no trace msg
(stest/unstrument `d/bar-me)
d/bar-me
;; #object[demo1.ns1$eval1713$fn__1714$G__1704__1723 0x3d83ab4e "demo1.ns1$eval1713$fn__1714$G__1704__1723@3d83ab4e"]
;; d/bar-me back to its original value, as one might hope
(d/bar-me 100 -1)

;; Now, what happen if we do not disable them in "stack order",
;; i.e. enable instrument and then trace, then disable instrument
;; first?
d/bar-me
;; #object[demo1.ns1$eval1713$fn__1714$G__1704__1723 0x3d83ab4e "demo1.ns1$eval1713$fn__1714$G__1704__1723@3d83ab4e"]
(d/bar-me 100 -1)
(stest/instrument `d/bar-me)
d/bar-me
;; #object[clojure.spec.test.alpha$spec_checking_fn$fn__2943 0x6d03693c "clojure.spec.test.alpha$spec_checking_fn$fn__2943@6d03693c"]
(d/bar-me 100 -1)
(t/trace-vars d/foo d/bar-me)
d/bar-me
;; #object[clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616 0x746844fe "clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616@746844fe"]
(d/bar-me 100 -1)
(stest/unstrument `d/bar-me)
;; NOTE: unstrument returned [] in this case, whereas when it was called earlier, I believe it always returned the vector [demo1.ns1/bar-me]
d/bar-me
;; NOTE: Value is the same as before unstrument call
;; #object[clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616 0x746844fe "clojure.tools.trace$trace_var_STAR_$fn__1615$tracing_wrapper__1616@746844fe"]
(d/bar-me 100 -1)

;; call above shows that trace msgs and instrument are both still
;; enabled.  It appears that unstrument did not change value of
;; d/bar-me Var, probably because it has some kind of internal check
;; that prevents it from making changes if the Var does not currently
;; have the value that instrument changed it to.

(t/untrace-vars d/foo d/bar-me)
d/bar-me
(d/bar-me 100 -1)


;; After enabling trace for d/foo and d/bar-me

;; + Evaluating the calls for the java.lang.Long protocol functions
;;   above will show tracing output.

;; + Evaluating the calls for the demo1.ns1.Demo1Type implementations
;;   will _not_ show tracing output.  TBD: Why not?  What is the
;;   difference between these two implementations that causes this
;;   difference?

;; Is this an easily-fixed bug in tools.trace?

;; Is there some other tracing tools other than tools.trace that can
;; do this?


)
