(ns mzero.ai.ann.neanderthal-impl-test
  (:require [clojure.data.generators :as g]
            [clojure.test :refer [are is testing]]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.common :as mzc :refer [vect=]]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.neanderthal-impl :as sut]
            [mzero.ai.ann.neanderthal-impl-test-data
             :refer
             [test-ann-impl test-data]]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.ann.network-test :refer [test-layers]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.native :as nn]
            [mzero.ai.ann.losses :as mzl]))

(deftest sequential-fp-speed-test
  :unstrumented
  (testing "A complete pass on 4 layers should take about 1 ms ( sum
    of ops time * 4), so less than 2ms (niceness). Strangely seems
    twice as fast as simultaneous fp"
    (let [dim 1024
          ;; variable dims between layers to check dimensional correctness
          dims [dim (inc dim) (+ dim 2) (+ dim 3) (+ dim 4)]
          ndt-impl
          (mzann/initialize (sut/->NeanderthalImpl)
                            (mzn/new-layers dims mzi/draft1-sparse-weights 42)
                            {:act-fns mza/usual
                             :label-distribution-fn mzld/softmax
                             :loss-gradient-fn
                             (partial mzl/cross-entropy-loss-gradient mzld/softmax)})
          inputs
          (binding [g/*rnd* (java.util.Random. 42)]
            [(vec (repeatedly dim #(g/float)))])
          forward-pass
          (u/timed (vec (repeatedly 100 #(sut/sequential-forward-pass! ndt-impl inputs true))))
          single-pass-time
          (/ (first forward-pass) 100)]
      (is (< single-pass-time 2)))))

(deftest forward-pass-test-small-without-plusb
  (testing "Test of forward pass on hand-computed values for weights and
  outputs, on small layers."
    (let [layer1
          {::mzn/inputs [0.0 0.0 0.0] ;; [.5 .425 1.0] then [.1 .225 1.0]
           ::mzn/weights (mzc/transpose [[-0.3 0.8 1.0] [0.5 2.0 -0.2]])
           ::mzn/raw-output-gradients [0.0 0.0]
           ::mzn/raw-outputs [0.0 0.0]
           ;; unactivated [1.19 0.9] [0.15 0.5]
           ::mzn/outputs [0.0 0.0]} ;; af [1.0 0.9] then [0.0 0.5]
          layer2
          {::mzn/inputs (::mzn/outputs layer1)
           ::mzn/weights (mzc/transpose [[0.7 0.7] [2.0 0.1]])
           ::mzn/raw-output-gradients [0.0 0.0]
           ::mzn/raw-outputs [0.0 0.0]
           ;; unactivated [1.33 2.09] then [0.35 0.05]
           ;; af [1.0 1.0] then [0.35 0.0]
           ::mzn/outputs [0.0 0.0]} 
          layer3
          {::mzn/inputs (::mzn/outputs layer2)
           ::mzn/weights (mzc/transpose [[6.0 -5.7] [0.3 -0.3]])
           ::mzn/raw-output-gradients [0.0 0.0]
           ::mzn/raw-outputs [0.0 0.0]
           ;; unactivated [0.3 0.0] then [2.1 0.105]
           ;; af [0.3 0.0] then [1.0 0] but no af on last layer
           ::mzn/outputs [0.0 0.0]}
          layers [layer1 layer2 layer3]
          ndt-impl
          (-> (mzann/initialize (sut/->NeanderthalImpl)
                                layers
                                {:act-fns mza/usual
                                 :label-distribution-fn mzld/softmax
                                 :loss-gradient-fn (partial mzl/cross-entropy-loss-gradient mzld/softmax)})
              (sut/sequential-forward-pass! [[0.5 0.425 1.0]] false))]
      (doseq [[layer-idx result] [[0 [[1.0 0.9]]]
                                  [1 [[1.0 1.0]]]
                                  [2 [[0.3 0.0]]]]]
        (is (mzc/coll-almost= (mzann/layer-data ndt-impl layer-idx "outputs") result)))
      (let [ndt-impl
            (sut/sequential-forward-pass! ndt-impl [[0.1 0.225 0.0]] false)]
        (is (mzc/coll-almost= (mzann/layer-data ndt-impl 0 "outputs") [[0.0 0.5]]))
        (is (mzc/coll-almost= (mzann/layer-data ndt-impl 1 "outputs") [[0.35 0.0]]))
        (is (mzc/coll-almost= (mzann/layer-data ndt-impl 2 "raw-outputs")
                         [[2.1 0.105]]))))))


(deftest forward-pass-test-small-full
  (let [[layer1 layer2 layer3] test-layers
        test-neanderthal-impl
        (-> (mzann/initialize (sut/->NeanderthalImpl)
                              [layer1 layer2 layer3]
                              {:act-fns mza/usual
                               :label-distribution-fn mzld/softmax
                               :loss-gradient-fn
                               (partial mzl/cross-entropy-loss-gradient mzld/softmax)})
            (mzann/forward-pass! [[0.5 0.425 1.0]]))]
    (is (mzc/coll-almost= (mzann/layer-data test-neanderthal-impl 0 "outputs")
                          [[1.0 1.0]]))
    (is (mzc/coll-almost= (mzann/layer-data test-neanderthal-impl 1 "outputs")
                          [[1.0 1.0]]))
    (is (mzc/coll-almost= (mzann/output test-neanderthal-impl) [[0.3 0.0]]))
    (let [test-neanderthal-impl
          (mzann/forward-pass! test-neanderthal-impl [[0.1 0.225 0.0]])]
      
      (is (mzc/coll-almost= (mzann/layer-data test-neanderthal-impl 0 "outputs")
                          [[0.0 1.0]]))
      (is (mzc/coll-almost= (mzann/layer-data test-neanderthal-impl 1 "outputs")
                          [[0.7 1.0]]))
      (is (mzc/coll-almost= (mzann/output test-neanderthal-impl) [[-1.5 -0.09]])))))

(deftest forward-pass-test-large
  (testing "Test of forward pass on machine computed values for weights and
  outputs, on large layers."
    (let [test-impl (test-ann-impl)
          last-layer-index (dec (mzann/nb-layers test-impl))]
      (doseq [in-out-pair test-data]
        (let [test-impl (mzann/forward-pass! test-impl [(:in in-out-pair)])]
          (is (mzc/coll-almost=
               [(:out in-out-pair)]
               (mzann/layer-data test-impl last-layer-index "outputs")))))))
  (testing "Test of forward pass with batch size 2"
    (let [test-impl (test-ann-impl)
          last-layer-index (dec (mzann/nb-layers test-impl))
          merge-for-batch-2
          (fn [io-pairs]
            (map #(hash-map :in (vector (:in %1) (:in %2))
                            :out (vector (:out %1) (:out %2)))
                 io-pairs
                 (rest io-pairs)))]
      (doseq [in-out-pair (merge-for-batch-2 test-data)]
        (let [test-impl
              (mzann/forward-pass! test-impl (:in in-out-pair))]
          (is (-> (mzann/layer-data test-impl last-layer-index "outputs")
                  (mzc/coll-almost= (:out in-out-pair))))))))
  (testing "Test of forward pass with batch size 4"
    (let [test-impl (test-ann-impl)
          last-layer-index (dec (mzann/nb-layers test-impl))
          merge-for-batch-4
          {:in (vec (map :in test-data))
           :out (vec (map :out test-data))}]
      (let [test-impl
            (mzann/forward-pass! test-impl (:in merge-for-batch-4))]
        (is (mzc/coll-almost=
             (:out merge-for-batch-4)
             (mzann/layer-data test-impl last-layer-index "outputs")))))))

(defn- merge-weights-updates
  [initial-weights & weights-updates]
  (let [merge-single-weight-update
        (fn [initial-val & val-updates]
          (apply + (* initial-val (- 1 (count val-updates))) val-updates))
        merge-row-updates
        (fn [initial-row & row-updates]
          (apply map merge-single-weight-update initial-row row-updates))]
    (apply map merge-row-updates initial-weights weights-updates)))

(deftest backward-pass-test-large
  (let [first-weights #(mzann/tens->vec % (-> % :layers first ::sut/weights))
        last-weights #(mzann/tens->vec % (-> % :layers last ::sut/weights))
        initial-weights
        [(first-weights (test-ann-impl)) (last-weights (test-ann-impl))]]
    (testing "Backward pass for batch size 1"
      (doseq [test-datapoint test-data]
        (let [test-impl
              (-> (test-ann-impl)
                  (mzann/forward-pass! [(:in test-datapoint)])
                  (mzann/backward-pass! [(:target test-datapoint)]))]
          (testing "Meaningful update : asum of weights diff is at least > 0.1"
            (is (not (u/almost= (->> (flatten (first-weights test-impl))
                                     (map - (flatten (first initial-weights)))
                                     (map #(Math/abs %))
                                     (apply +))
                                0.0 0.1)))
            (is (not (u/almost= (->> (flatten (last-weights test-impl))
                                     (map - (flatten (last initial-weights)))
                                     (map #(Math/abs %))
                                     (apply +))
                                0.0 0.1))))
          (testing "Weights are updated with correct values"
            (is (mzc/coll-almost= (first-weights test-impl)
                                  (:updated-first-weights test-datapoint)))
            (is (mzc/coll-almost= (last-weights test-impl)
                                  (:updated-last-weights test-datapoint)))))))
    (testing "Batch size 2"
      (let [batch2-data
            (map (fn [dp1 dp2]
                   {:in (vector (:in dp1) (:in dp2))
                    :target (vector (:target dp1) (:target dp2))
                    :updated-first-weights
                    (merge-weights-updates (first initial-weights)
                                           (:updated-first-weights dp1)
                                           (:updated-first-weights dp2))
                    :updated-last-weights
                    (merge-weights-updates (last initial-weights)
                                           (:updated-last-weights dp1)
                                           (:updated-last-weights dp2))})
                 test-data (rest test-data))]
        (doseq [test-datapoint batch2-data]
          (let [test-impl
                (-> (test-ann-impl)
                    (mzann/forward-pass! (:in test-datapoint))
                    (mzann/backward-pass! (:target test-datapoint)))]
            (is (mzc/coll-almost= (first-weights test-impl)
                                  (:updated-first-weights test-datapoint)))
            (is (mzc/coll-almost= (last-weights test-impl)
                                  (:updated-last-weights test-datapoint)))))))
    (testing "Batch size 4"
      (let [batch4-datapoint
            {:in (vec (map :in test-data))
             :target (vec (map :target test-data))
             :updated-first-weights
             (apply merge-weights-updates (first initial-weights)
                    (map :updated-first-weights test-data))
             :updated-last-weights
             (apply merge-weights-updates (last initial-weights)
                    (map :updated-last-weights test-data))}
            test-impl
            (-> (test-ann-impl)
                (mzann/forward-pass! (:in batch4-datapoint))
                (mzann/backward-pass! (:target batch4-datapoint)))]
        (is (mzc/coll-almost= (first-weights test-impl)
                              (:updated-first-weights batch4-datapoint)))
        (is (mzc/coll-almost= (last-weights test-impl)
                              (:updated-last-weights batch4-datapoint)))))))

(deftest clear-neuron-test
  (let [test-impl (test-ann-impl)]
    (mzann/clear-neuron! test-impl -1 1)
    (is (= 0.0 (nc/asum (-> test-impl :layers last ::sut/weights (nc/row 1)))))
    (mzann/clear-neuron! test-impl -1 0)
    (is (= 0.0 (nc/asum (-> test-impl :layers last ::sut/weights (nc/row 0)))))))

