(ns xp1000
  "Run 1000 times games with 2 types of player and compares the results,
  giving the good stats."
  (:require [claby.ai.main :as aim]
            [claby.ai.world :as aiw]
            [claby.game.generation :as gg]
            [claby.game.state :as gs]
            [claby.utils.utils :as u]
            [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]))

(defn mean
  "Mean value of a sequence of numbers"
  [l]
  (double (/ (reduce + l) (count l))))

(defn std
  "Standard deviation of a sequence of numbers."
  [l]
  (let [mean-val (mean l)]
    (Math/sqrt (-> (reduce + (map #(* % %) l))
                   (/ (count l))
                   (- (* mean-val mean-val))))))

(defn- display-stats [title measures]
  (printf "%s (%d measures) : Mean %,4G +- %,4G || Sum %,4G\n"
          title (count measures) (mean measures) (std measures)
          (double (reduce + measures))))

(defn measure
  "Run `xp-fn` for each parameter given in `args-list`, and computes
  stats on the result via `measure-fn`, which may return either a coll
  of measures, or a coll of coll of measures"
  [xp-fn measure-fn args-list]
  (let [valid-measure-seqs?
        #(s/valid? (s/coll-of (s/coll-of number?)) %)
        measures
         (->> (pmap (partial apply xp-fn) args-list)
              (map measure-fn))
        measure-seqs
        (cond->> measures (number? (first measures)) (map vector))
        get-nth-measures
        (fn [i] (map #(nth % i) measure-seqs))]

    (if (valid-measure-seqs? measure-seqs)
      (map get-nth-measures (range (count (first measure-seqs))))
      nil)))

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
  [board-size nb-steps nb-sims constr nb-xps]
  (let [game-args
        (format "-s %d -n %d -o '{:node-constructor %s :nb-sims %d}'"
                board-size nb-steps constr nb-sims)
        timed-go
        #(u/timed (aim/go (str "-v WARNING -t tree-exploration " game-args) %))
        measure-fn
        #(vector (/ (* board-size 2 nb-steps nb-sims 1000) (first %))
                       (-> % second :world ::aiw/game-step))
        random-worlds ;; seeded generation of game states, always same list
        (map (comp list aiw/get-initial-world-state)
             (gg/generate-game-states nb-xps board-size 41))]
    
    (display-measures (measure timed-go measure-fn random-worlds)
                      game-args
                      "Tree exploration subsims/s")))

(defn -runcli [& args]
  (apply (resolve (symbol (str "xp1000/" (first args))))
         (map read-string (rest args))))
