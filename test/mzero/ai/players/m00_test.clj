(ns mzero.ai.players.m00-test
  (:require [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw :refer [world]]
            [mzero.ai.main :as aim]
            [clojure.tools.logging :as log]
            [clojure.data.generators :as g]))

(defn run-n-steps
  [player size world acc]
  (if (> size 0)
    (let [next-movement
          (-> player (aip/update-player world) :next-movement)
          next-world
          (aiw/compute-new-state
           (assoc world ::aiw/requested-movements {:player next-movement}))]
      
      (recur player (dec size) next-world (conj acc next-movement)))
    acc))

(deftest m00-randomness
  (let [test-world (world 25 43)
        m00-opts
        {:seed 40 :vision-depth 4 :layer-dims [18 30]}
        m00-player
        (aip/load-player "m00" m00-opts test-world)
        dl-updates
        (u/timed (run-n-steps m00-player 1000 test-world []))]
    
    (testing "Chosen direction approximately random, more than say 150
    each dir. Note: this is not a real property fof m00. Here we
    purposely found a setup of layers, patterns & inputs exhibiting
    this property, for testing purposes."
      (is (every? #(> % 150) (map (frequencies (second dl-updates)) ge/directions))))))

(deftest ^:integration m00-run
  :unstrumented
  (testing "Speed should be above ~2.5 GFlops, equivalently 25 iters per sec"
    (let [test-world (world 30 42)
          expected-gflops 2 ;; more than 2 Gflops => average probably more than 2.5
          expected-iters-sec 20
          ;; layer constant ~= nb of ops for each matrix elt for a layer
          ;; layer nb is inc'd to take into account 
          layer-nb 8 dim 1024 layer-constant 10 steps 250
          forward-pass-ops (* dim dim (inc layer-nb) layer-constant steps)
          m00-opts
          {:seed 40 :vision-depth 4 :layer-dims (repeat layer-nb dim)}
          game-opts
          (aim/parse-run-args "-v WARNING -n %d -t m00 -o'%s'" steps m00-opts)
          time-ms
          (first (u/timed (aim/run game-opts test-world)))
          actual-gflops
          (/ forward-pass-ops time-ms 1000000)
          iterations-per-sec
          (/ steps time-ms 0.001)]
      #_(log/info actual-gflops " GFlops, " iterations-per-sec " iters/sec")
      (is (< expected-gflops actual-gflops))
      (is (< expected-iters-sec iterations-per-sec)))))


