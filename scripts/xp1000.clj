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

(defn -main [nb-xps player-type]
  (println "Starting xp...")
  (time
   (let [game-args
         {:game-step-duration 1
          :player-step-duration 1
          :logging-steps 0
          :board-size 20
          :player-type player-type
          :logging-level java.util.logging.Level/WARNING
          :game-runner gr/->WatcherRunner}
         arg-sequence
         (repeatedly (Integer/parseInt nb-xps) (constantly game-args))
         measures
         (pmap #(::aiw/game-step (aim/run %)) arg-sequence)]
     (display-stats "Steps per game" measures))))

