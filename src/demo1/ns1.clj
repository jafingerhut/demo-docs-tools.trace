(ns demo1.ns1)

(defn bar
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defprotocol Demo1Proto1
  (foo [this])
  (bar-me [this] [this y]))

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


(comment

(require '[clojure.tools.trace :as t])
(require '[demo1.ns1 :as d] :reload)

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


;; With Oracle JDK 8, Clojure 1.9.0, tools.trace 0.7.9, whether trace
;; messages appeared or not is in comments marked "v1".

;; v1: no trace messages for protocol method calls on Demo1Type
(def d1 (demo1.ns1.Demo1Type. 5 10 15))
(d/foo d1)
(d/bar-me d1)
(d/bar-me d1 -1)

;; v1: yes trace message for unimplemented protocol method call on
;; Demo1Type, at least for the call, but not return, because exception
;; thrown before return.
(d/baz d1)
(d/guh d1 5)

;; v1: yes trace messages for protocol method calls on java.lang.Long
(d/foo 100)
(d/bar-me 100)
(d/bar-me 100 8)
(d/baz 100)
(d/guh 100 5)

;; v1: yes trace messages for protocol method calls on Demo1OtherType,
;; where all of the implementations were given using extend-type.
(def d2 (demo1.ns1.Demo1OtherType. 31 63))
(d/foo d2)
(d/bar-me d2)
(d/bar-me d2 -1)
(d/baz d2)
(d/guh d2 5)


;; v1: yes trace message for record constructor functions ->Demo1Rec1
;; and map->Demo1Rec1
(def r1 (d/->Demo1Rec1 17 19))
(def r2 (d/map->Demo1Rec1 {:a "a" :b "b"}))

;; v1: no trace messages for protocol method calls on a record, where
;; the method implementations were given within the defrecord form.
(d/foo r1)
(d/bar-me r1)
(d/bar-me r1 23)

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

d/bar-me
(fn? d/bar-me)
(t/trace-vars d/foo d/bar-me)
(t/untrace-vars d/foo d/bar-me)

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
