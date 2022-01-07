(ns mzero.ai.players.m00-test
  (:require [clojure.test :refer [is testing]]
            [clojure.tools.logging :as log]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.common :as mzc]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.neanderthal-impl :as mzni]
            [mzero.ai.main :as aim]
            [mzero.ai.player :as aip]
            [mzero.ai.players.m0-modules.motoneurons :as mzm]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.players.m00 :as sut]
            [mzero.ai.world :as aiw :refer [world]]
            [mzero.game.generation :as gg]
            [mzero.game.state :as gs]
            [mzero.utils.testing :refer [check-spec deftest]]
            [mzero.utils.utils :as u]))

(def seed 44)
(defn run-n-steps
  [player size world acc]
  (if (> size 0)
    (let [next-player
          (aip/update-player player world)
          next-movement
          (-> next-player :next-movement)
          next-world
          (aiw/compute-new-state
           (assoc world ::aiw/requested-movements
                  (if next-movement {:player next-movement} {})))]
      
      (recur next-player (dec size) next-world (conj acc next-movement)))
    acc))

(def reference-m00-opts {:seed seed
                         :layer-dims (repeat 3 512)
                         :weights-generation-fn mzi/angle-sparse-weights
                         :ann-impl {:label-distribution-fn mzld/ansp
                                    :act-fns mza/usual}})

(def reference-world (aiw/world 30 seed))
;; A player is stateful, due to its field RNG :/ So, function to get it from start 
(def reference-player #(aip/load-player "m00" reference-m00-opts reference-world))

(deftest m00-instrumented-check
  (testing "Run player with fixed set of options, should therefore
  always end up at the same place with the same score. Also used to
  check specs on fn calls during a real run.
  
  NOT used to check any kind of speed/score performance of any
  specific options, therefore the default options chosen below should
  not matter")
  (let [m00-opts-difseed (update reference-m00-opts :seed inc)
        result1
        (->> (aim/run (aim/parse-run-args "-v WARNING -n 1000")
               reference-world
               (reference-player))
             :world ::gs/game-state)
        result2
        (->> (aip/load-player "m00" m00-opts-difseed reference-world)
             (aim/run (aim/parse-run-args "-v WARNING -n 1000") reference-world)
             :world ::gs/game-state)]
    (is (= (-> result1 ::gs/player-position) [22 12]))
    (is (= (-> result1 ::gs/score) 29))
    (is (= (-> result2 ::gs/player-position) [2 28]))
    (is (= (-> result2 ::gs/score) 42))))

(deftest ^:integration m00-run
  :unstrumented
  ;; TODO : regularly review gflops values

  ;; Torch impl deactivated since breaking change in v0.0.5, see
  ;; version doc for more info

  ;; WARNING: Gflops used to be correlated to actual gflops when there
  ;; was only a forward pass; now there is a backward pass which does
  ;; not always occur, so Gflops are an indicative, relative measure
  (doseq [ann-impl ["neanderthal-impl"]]
    (testing (str ann-impl " - Speed should be above 25 GFlops,
    equivalently 250 iters per sec")
      (let [test-world (world 30 seed)
            expected-gflops 25
            expected-iters-sec 250
            ;; layer constant ~= nb of ops for each matrix elt for a layer
            ;; layer nb is inc'd to take into account 
            layer-nb 8 dim 1024 layer-constant 10 steps 250
            forward-pass-ops (* dim dim (inc layer-nb) layer-constant steps)
            m00-opts {:seed seed :layer-dims (repeat layer-nb dim) :ann-impl {}}
            game-opts
            #(aim/parse-run-args "-v WARNING -n %d -t m00 -o'%s'" % m00-opts)
            ;; prewarm : load player
            [time-ms-loading {:keys [world player]}] 
            (u/timed (aim/run (game-opts 1) test-world))
            time-ms
            (first (u/timed (aim/run (game-opts steps) world player)))
            actual-gflops
            (/ forward-pass-ops time-ms 1000000)
            iterations-per-sec
            (/ steps time-ms 0.001)]
        (log/info
         (format  "%,3G GFlops, %,3G iters/sec, %,3G s loading time"
                  actual-gflops iterations-per-sec (/ time-ms-loading 1000)))
        (is (< expected-gflops actual-gflops))
        (is (< expected-iters-sec iterations-per-sec))))))

(deftest m00-initialization
  (testing "m00 player can be initialized with an already-existing ann"
    (let [ann
          (#'sut/create-ann-impl-from-opts
           {:layer-dims (repeat 11 11)
            :weights-generation-fn mzi/angle-sparse-weights
            :ann-impl (assoc sut/ann-default-opts :act-fns mza/spike)}
           25)
          test-world (aiw/world 26 26)
          m00 (aip/load-player "m00" {:layer-dims (repeat 11 11) :ann-impl ann} test-world)
          {{:keys [ann-impl]} :player}
          (aim/run (aim/parse-run-args "-v WARNING -n 1") test-world m00)]
      ;; act-fns should not be equal to default
      (is (not= (:act-fns sut/ann-default-opts) (mzann/act-fns ann-impl)))
      ;; should be equal to what we initialized it with
      (is (= mza/spike (mzann/act-fns ann-impl)))
      ;; and layers too
      (is (= (mzann/nb-layers ann-impl) 12))
      (is (= (count (first (mzann/layer-data ann-impl 1 "inputs"))) 12)))))
