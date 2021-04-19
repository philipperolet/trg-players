(ns mzero.utils.xp
  "Utilities for performing experiments."
  (:require [clojure.spec.alpha :as s]
            [mzero.utils.utils :as u]))

(defn mean
  "Mean value of a sequence of numbers"
  [l]
  (double (/ (reduce + l) (count l))))

(defn std
  "Standard deviation of a sequence of numbers. Returns -1.0 if only 1 measure"
  [l]
  (if (= (count l) 1)
    -1.0
    (let [mean-val (mean l)]
      (Math/sqrt (-> (reduce + (map #(let [val (- % mean-val)] (* val val)) l))
                     (/ (dec (count l))))))))

(defn confidence
  "Estimation of 90% confidence interval half-size, assuming normal
  distribution, based on an approximation of student t
  distribution. Not exact and unreliable under 5 measures, returns -1.0
  in this case"
  [measures]
  (let [nb (count measures)]
    (if (>= nb 5)
      (let [t-estim (+ 1.65 (/ 1.6 nb))]
        (* (std measures) t-estim (/ (Math/sqrt nb))))
      -1.0)))

(def display-string "
%s (%d measures)
---
Mean %,4G +- %,4G
Std %,4G
Sum %,4G
---
")

(defn display-stats
  "Display usual stats given a set of `measures`"
  [title measures]
  (printf display-string
          title (count measures)
          (mean measures) (confidence measures)
          (std measures)
          (double (reduce + measures))
          (str measures)))

(defn measure
  "Run `xp-fn` for each parameter given in `args-list`, and computes
  stats on the result via `measure-fn`, which may return either a coll
  of measures, or a coll of coll of measures
  `map-fn` allows to specify what function will be used to process experiments,
  usually `map` for seq processing & `pmap` for parallel processing"
  ([xp-fn measure-fn args-list map-fn]
   (let [valid-measure-seqs?
         #(s/valid? (s/coll-of (s/coll-of number?)) %)
         measures
         (->> (map-fn (partial apply xp-fn) args-list)
              (map measure-fn))
         measure-seqs
         (cond->> measures
           (number? (first measures)) (map vector))
         get-nth-measures
         (fn [i] (map #(nth % i) measure-seqs))]

     (if (valid-measure-seqs? measure-seqs)
       ;; vec used to realize lazy seq
       (vec (map get-nth-measures (range (count (first measure-seqs)))))
       nil)))
  ([xp-fn measure-fn args-list]
   (measure xp-fn measure-fn args-list u/ipmap)))

(defn display-measures
  ([measures data name]
   (println (format "---\nXp '%s'\nData %s\n---" name data))
   (if measures
     (dotimes [n (count measures)]
       (display-stats (str name " " n) (nth measures n)))
     (throw (Exception.
             (str "Invalid measurements, e.g. " (first measures))))))

  ([measure-seqs data]
   (display-measures measure-seqs data "Measure")))

(defn timed-measures
  "Convenience function to get `nb-xps` measures of `xp-fn` with `args`
  list. Measurements are computed via `measure-fn` applied on a
  **timed** execution of xp-fn, a pair (timing, result)."
  [xp-fn args nb-xps measure-fn]
  (let [timed-fn
         (fn [& args]
           (u/timed (apply xp-fn args)))]
    (measure timed-fn measure-fn (repeat nb-xps args) map)))

