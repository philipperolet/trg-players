(ns claby.utils
  "A few utilities."
  #?@
   (:clj
    [(:require
      [clojure.spec.test.alpha :as st]
      [clojure.string :as str]
      [clojure.test :refer [deftest is testing]])]
    :cljs
    [(:require
      [cljs.spec.test.alpha :as st]
      [cljs.test :refer-macros [deftest]]
      [clojure.string :as str])]))

(defn check-results 
  "Returns a nicer version of test.check results for a namespace"
  [nsspecs]
  (->> (st/check #?(:cljs 'nsspecs :clj nsspecs))
       (map #(select-keys (st/abbrev-result %) [:failure :sym]))
       (remove #(not (:sym %)))
       ;; if spec conformance test failed, returns failure data 
       (filter #(:failure %))))

(defn check-failure
  "Returns a more pretty version of results of check on a symbol.
  
   If spec conformance test failed, returns failure data, else nil"
  ([sym opts]
   (-> (st/check #?(:cljs 'sym :clj sym) opts)
       first
       st/abbrev-result
       (select-keys [:failure :sym])
       :failure))
  ([sym] (check-failure sym {})))

(defmacro check-spec
  "Creates a test with deftest for a given symbol, with given options."
  ([sym opts]
   (-> `((testing ~(str "Testing spec " sym)
           (is (not (check-failure ~sym ~opts)))))
       (conj (symbol (str "check-spec-" (last (str/split (str sym) #"/")))))
       (conj 'deftest)))
  ([sym] `(check-spec ~sym {})))

(defmacro check-all-specs
  "Check-spec for all specs in namespace ns"
  [symbs]
  (let [ns-specs (st/enumerate-namespace #?(:cljs 'symbs :clj symbs))]
    (-> (map (fn [to-check]
               `(testing ~(str "Testing spec " to-check)
                  (is (not (check-failure (quote ~to-check))))))
             ns-specs)
        (conj 'check-namespace-specs)
        (conj 'deftest))))

(defn count-calls
  "Used in with-redefs to count function calls during testing.

  Returns a new function that mimics `f` but counts the number of
  calls and stores it as metadata.
  Resets count when call count is checked."
  [f]
  (let [n (atom 0)]
    (with-meta
      (comp (fn [x] (swap! n inc) x) f)
      {:call-count (fn [] (first (reset-vals! n 0)))
       :private false})))

"ModSubVector : A view on vector vect that is a subvector starting at
`start`, of length `length`, such that:

- start & length can be bigger than vect count or smaller than 0 (via
  modulo (count vect)). In particular the subvec restarts from zero if
  start + length > (count vect)

- the resulting subvector is of size `length`, its first
element (index 0) is vect[position], its last element is
vect[(position + length) % vect-size]."

(deftype ModSubVector [vect start length]
  clojure.lang.Indexed
  (nth [this n m]
    (if (< -1 n length)
      (nth vect (mod (+ start n) (count vect)))
      m))
  (nth [this n]
    (or (nth this n nil)
        (throw (IndexOutOfBoundsException.))))
  
  clojure.lang.Counted
  (count [this] length)
  clojure.lang.IPersistentVector

  clojure.lang.IPersistentCollection
  (equiv [this obj]
    (cond
      (indexed? obj)
      (and (= (count obj) (count this))
           (loop [cnt (dec (count this))]
             (if (= (nth obj cnt) (nth this cnt))
               (or (zero? cnt)
                   (recur (dec cnt)))
               false)))

      (seqable? obj)
      (= (seq this) (seq obj))

      :else
      false))
  
  clojure.lang.Seqable
  (seq [this]
    (reify clojure.lang.ISeq
      (first [this_s] (vect (mod start (count vect))))
      (next [this_s]
        (if (= 1 length) nil (seq (ModSubVector. vect (inc start) (dec length)))))
      (seq [this_s] this_s)
      (more [this_s] (or (next this_s) '()))
      (equiv [this_s obj]
        (and (seqable? obj)
             (let [obj_s (seq obj)]
               (and (= (first this_s) (first obj_s))
                    (= (next this_s) (next obj_s)))))))))

(defn modsubvec
  "Return a subvector of `vect` starting at `start`, of length
  `length`, using modulo if start+length > (count vect)"
  [vect start length]
  {:pre [(vector? vect)
         (int? start)
         (nat-int? length)
         (< 0 length (inc (count vect)))]}
  (->ModSubVector vect start length))
