(ns mzero.ai.players.m00-test
  (:require [mzero.ai.players.m00 :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw :refer [world]]
            [mzero.ai.players.activation :as mza]
            [uncomplicate.neanderthal.native :refer [dge dv]]
            [uncomplicate.neanderthal.core :as nc]))

(deftest direction-from-output-test
  (is (= (#'sut/direction-from-output (dv 1.3 0.32 0.11))
         :right)) ;; 0.73 => 1 => right
  (is (= (#'sut/direction-from-output (dv 0.33 0.34 0.33 0.0 1.0))
         :up))
  (is (= (#'sut/direction-from-output (dv 0.13 0.14))
         :left)))

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


(defn- sparsify-weights
  [player]
  (let [randomly-nullify
        (fn ^double [^long _ ^long _ ^double v] (if (< 0.9 (rand)) 0.0 v))
        sparsify-1-layer
        (fn [{:keys [::mza/weights]}]
          (nc/alter! weights randomly-nullify))]
    (doall (map sparsify-1-layer (-> player :layers)))
    player))

(deftest m00-randomness
  (let [test-world (world 25 41)
        m00-opts
        {:seed 40 :vision-depth 3 :layer-dims [20 20 20]}
        m00-player
        (-> (aip/load-player "m00" m00-opts test-world)
            sparsify-weights)
        dl-updates
        (u/timed (run-n-steps m00-player 1000 test-world []))]
    
    (testing "Random direction approximately correct, about 250 of each dir"
      (is (every? #(> % 220) (map (frequencies (dl-updates 1)) ge/directions))))))

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

