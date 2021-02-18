(ns mzero.ai.players.dummy-luno-test
  (:require [mzero.ai.players.dummy-luno :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [mzero.game.events :as ge]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]
            [mzero.ai.main :as aim]
            [uncomplicate.neanderthal.native :refer [dge]]))

(deftest get-int-from-decimals
  (is (= 33 (#'sut/get-int-from-decimals 32.13325)))
  (is (= 72 (#'sut/get-int-from-decimals 0.3725))))

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

(deftest get-real-valued-senses-test
  (let [test-world
        (aiw/get-initial-world-state (first (gg/generate-game-states 1 25 41)))]
    (is (= (#'sut/get-real-valued-senses test-world 2)
           (map float [0 0.5 0 0 0 0 0 0.5 0 0 0.5 0 0 0 0 0 1 1 1 1 1 1 1 1 1])))))

(deftest forward-pass-correctness
  (let [input (dge 1 3 [1.0 0.5 2.0])
        hidden (dge 3 4 [0 0 0 1 1 1 -1 0.5 3.2 0 0 0])
        output (dge 4 1 [0.5 -0.5 3 -7])]
    (is (u/almost= (#'sut/forward-pass input hidden output) 15.2 0.00001))))

(deftest dummy-luno-randomness
  (let [test-world
        (aiw/get-initial-world-state (first (gg/generate-game-states 1 25 41)))
        dummy-luno
        (aip/load-player "dummy-luno" {:seed 40} test-world)
        dl-updates
        (u/timed (run-n-steps dummy-luno 1000 test-world []))]
    
    (testing "Random direction approximately correct, about 250 of each dir"
      (is (every? #(> % 220) (map (frequencies (dl-updates 1)) ge/directions))))))

(deftest ^:integration dummy-luno-fast-enough
  :unstrumented
  (testing "Fast enough, more than 1K cycles/s on a size 50 board for 1 layer"
    (let [initial-world
          (aiw/get-initial-world-state
           (first (gg/generate-game-states 1 50 41)))
          player-options
          "{:hidden-layer-size 10000 :seed 40}"
          get-game-args
          #(aim/parse-run-args
            "-t dummy-luno -n %d -v WARNING -o '%s'" % player-options)]
      (is (< (first (u/timed (aim/run (get-game-args 1000) initial-world)))
             1000)))))


