(ns mzero.ai.players.m00-test
  (:require [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw :refer [world]]
            [mzero.ai.main :as aim]
            [mzero.ai.players.m00 :as sut]
            [mzero.ai.players.network :as mzn]
            [uncomplicate.neanderthal.random :as rnd]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.vect-math :as nvm]
            [uncomplicate.neanderthal.core :as nc]))

(deftest sparsify-test
  (let [rng (rnd/rng-state nn/native-float 43)
        input-dim 100 nb-cols 1000
        w (rnd/rand-uniform! rng (nn/fge input-dim nb-cols))
        _ (#'sut/sparsify-weights [{::mzn/weights w}])
        nb-nonzero-weights (nc/sum (nvm/ceil (nvm/abs w)))
        nb-neg-weights (- (nc/sum (nvm/floor w)))
        neg-ratio (/ nb-neg-weights nb-nonzero-weights)
        total-ratio (/ nb-nonzero-weights (nc/dim w))
        expected-total-ratio (/ (sut/nonzero-weights-nb input-dim 0.5) input-dim)]
    (is (u/almost= neg-ratio sut/nwr (* 0.05 neg-ratio)))
    (is (u/almost= total-ratio expected-total-ratio (* 0.05 total-ratio)))))

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
        m00-opts {:seed 40 :layer-dims [18 30]}
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
          m00-opts {:seed 40 :layer-dims (repeat layer-nb dim)}
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


