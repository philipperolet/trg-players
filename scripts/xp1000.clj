(ns xp1000
  "Run 1000 times games with 2 types of player and compares the results,
  giving the good stats."
  (:require [claby.ai.main :as aim]
            [claby.ai.world :as aiw]
            [claby.game.generation :as gg]
            [claby.utils.utils :as u]
            [claby.utils.testing :refer [count-calls]]
            [claby.ai.players.tree-exploration :as te]
            [clojure.spec.alpha :as s]
            [claby.game.events :as ge]))

(defn mean
  "Mean value of a sequence of numbers"
  [l]
  (double (/ (reduce + l) (count l))))

(defn std
  "Standard deviation of a sequence of numbers."
  [l]
  (let [mean-val (mean l)]
    (Math/sqrt (-> (reduce + (map #(let [val (- % mean-val)] (* val val)) l))
                   (/ (dec (count l)))))))

(defn confidence
  "Estimation of 90% confidence interval half-size, assuming normal
  distribution, based on an approximation of student t
  distribution. Not exact and unreliable under 5 measures, returns -1.0
  in this case"
  [measures]
  (let [nb (count measures)]
    (if (>= nb 5)
      (let [t-estim (+ 1.28 (/ nb))]
        (* (std measures) t-estim (/ (Math/sqrt nb))))
      -1.0)))

(def display-string "
%s (%d measures)
===
Mean %,4G +- %,4G
Std %,4G
Sum %,4G
---
")

(defn- display-stats [title measures]
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
         (cond->> measures (number? (first measures)) (map vector))
         get-nth-measures
         (fn [i] (map #(nth % i) measure-seqs))]

     (if (valid-measure-seqs? measure-seqs)
       (map get-nth-measures (range (count (first measure-seqs))))
       nil)))
  ([xp-fn measure-fn args-list]
   (measure xp-fn measure-fn args-list pmap)))

(defn display-measures
  ([measures data name]
   (println (format "\n---\nStarting xp '%s' with data %s\n---\n" name data))
   (if measures
     (dotimes [n (count measures)]
       (display-stats (str name " " n) (nth measures n)))
     (throw (Exception.
             (str "Invalid measurements, e.g. " (first measures))))))

  ([measure-seqs data]
   (display-measures measure-seqs data "Measure")))

(defn -step-avg-xp [nb-xps player-type]
  (let [game-args (str  "-s 20 -v WARNING -t " player-type)]
    (display-measures
     (measure #(aim/go game-args)
              (comp ::aiw/game-step :world)
              (repeat (Integer/parseInt nb-xps) []))
        game-args)))

(defn -fastest-te-impl
  "A subsim is ~ an atomic operation in the tree exploration,
  somewhat similar to one random move. The speed we wish to measure is
  subsim/sec"
  [board-size constr nb-xps]
  (let [game-args
        (format "-n 10 -o '{:node-constructor %s :nb-sims 100}'" constr)
        timed-go
        (fn [world]
          (with-redefs [ge/move-player (count-calls ge/move-player)]
            (-> (aim/go (str "-v WARNING -t tree-exploration " game-args) world)
                u/timed
                (conj ((:call-count (meta ge/move-player)))))))
        measure-fn
        #(vector (/ (last %) (/ (first %) 1000))
                 (last %))        
        random-worlds ;; seeded generation of game states, always same list
        (map (comp list aiw/get-initial-world-state)
             (gg/generate-game-states nb-xps board-size 41))]
    
    (display-measures (measure timed-go measure-fn random-worlds map)
                      game-args
                      "Tree exploration op/s")))

(defn -compare-sht
  "For random, an op is just the number of steps played"
  [board-size nb-xps]
  (doseq [player-type ["exhaustive" "random" "tree-exploration"]]
    (let [timed-go
          #(u/timed (aim/go (str "-l 200000 -t " player-type) %))
          measure-fn 
          #(vector (first %)
                   (-> % second :world ::aiw/game-step))
          random-worlds
          (map (comp list aiw/get-initial-world-state)
               (gg/generate-game-states nb-xps board-size 41))]
      (display-measures (measure timed-go measure-fn random-worlds map)
                        player-type
                        "Steps and time"))))
(defn -runcli [& args]
  (apply (resolve (symbol (str "xp1000/" (first args))))
         (map read-string (rest args))))
