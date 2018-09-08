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
  (bar-me [this] (* this 3))
  (bar-me [this y] (/ this y)))


(defprotocol Demo1Proto2
  (baz [this])
  (guh [this y]))

(extend-type java.lang.Long
  Demo1Proto2
  (baz [this] [:baz-on-long this])
  (guh [this y] {:guh-on-long this :y y}))


(comment

(+ 1 2)
(require '[clojure.tools.trace :as t])
(require '[demo1.ns1 :as d] :reload)

(def d1 (demo1.ns1.Demo1Type. 5 10 15))
(d/foo d1)
(d/bar-me d1)
(d/bar-me d1 -1)

;; TBD: Why the exception below?  It occurs whether the only arg is
;; 100 as shown, or (Long. 100).

(d/foo 100)
(d/bar-me 100)
;; => ArityException Wrong number of args (1) passed to: ns1/eval1760/fn--1763  clojure.lang.AFn.throwArity (AFn.java:429)
(d/bar-me 100 8)

(d/baz 100)
(d/guh 100 5)

)
