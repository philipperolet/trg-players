(ns mzero.ai.players.m00-test
  (:require [mzero.ai.players.m00 :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw :refer [world]]))

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
  (let [test-world (world 25 42)
        m00-opts
        {:seed 40 :vision-depth 4 :layer-dims [18 30]}
        m00-player
        (-> (aip/load-player "m00" m00-opts test-world))
        dl-updates
        (u/timed (run-n-steps m00-player 1000 test-world []))]
    
    (testing "Chosen direction approximately random, more than 250 of
    each dir. Note: this is not a real property fof m00. Here we
    purposely found a setup of layers, patterns & inputs exhibiting
    this property, for testing purposes."
      (is (every? #(> % 200) (map (frequencies (dl-updates 1)) ge/directions))))))

#_(deftest ^:integration dummy-luno-fast-enough
  :unstrumented
  (testing "Fast enough, more than 1K cycles/s on a size 50 board for 1 layer"
    (let [player-options
          "{:hidden-layer-size 10000 :seed 40}"
          run-args
          (aim/parse-run-args "-v WARNING -t dummy-luno -n 1000 -o'%s'"
                              player-options)
          runtime
          (first (u/timed (aim/run run-args (world 50 41))))]
      (is (< runtime 1000)))))

