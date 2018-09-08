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


(comment

(require '[clojure.tools.trace :as t])
(require '[demo1.ns1 :as d] :reload)

(def d1 (demo1.ns1.Demo1Type. 5 10 15))
(d/foo d1)
(d/bar-me d1)
(d/bar-me d1 -1)

(d/foo 100)
(d/bar-me 100)
(d/bar-me 100 8)

(d/baz 100)
(d/guh 100 5)

;; Evaluating d/bar-me before and after trace-vars below, and again
;; after untrace-vars.  It restores back to the original value after
;; untrace-vars.  That makes sense, since I believe that tools.trace
;; works by 'wrapping' the original function in another one that
;; prints extra things when tracing is enabled, and restoring the
;; original function when tracing is turned off.

d/bar-me
(fn d/bar-me)
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
