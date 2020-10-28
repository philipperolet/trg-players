(ns xp1000
  "Run 1000 times games with 2 types of player and compares the results,
  giving the good stats."
  (:require [claby.ai.main :as aim]
            [claby.ai.world :as aiw]
            [claby.ai.game-runner :as gr]))

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
  (printf "%s (%d xps)\n---\nMean %.3f +- %.3f\n\n"
          title (count measures) (mean measures) (std measures)))

(defn xp
  [measure-fn nb-xps game-args name]
  (printf "\n---\nStarting xp '%s' with args %s\n---\n" name nb-xps game-args)
  (time
   (->> (pmap (fn [_] (aim/go game-args)) (range (Integer/parseInt nb-xps)))
        (map measure-fn)
        (display-stats "Steps per game")))
  (shutdown-agents))

(defn -step-avg-xp [nb-xps player-type]
  (xp (comp ::aiw/game-step :world)
      nb-xps
      (str  "-s 20 -v WARNING -t " player-type)
      "Step Average"))

