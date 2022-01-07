(ns mzero.ai.ann.shallow-mpann-test
  (:require [clojure.test :refer [is testing]]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.shallow-mpann :as sut]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.neanderthal-impl :as mzni]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.label-distributions :as mzld]
            [clojure.data.generators :as g]
            [mzero.ai.ann.common :as mzc]
            [mzero.utils.random :as mzr]
            [clojure.tools.logging :as log]
            [mzero.utils.utils :as u]))

(defrecord fakenn []
  mzann/ANN
  (-layer-data [this lindex lkey] (:currtoto this))
  (nb-layers [this] 3)
  (-tens->vec [this tensor] tensor))

(deftest run-passes-if-ready-test
  (with-redefs [mzann/forward-pass! #(assoc %1 :currtoto %2)
                mzann/backward-pass!
                (fn [ai i t d]
                  (assoc ai :fake-backprop (flatten t)))]
    (let [smpanns (sut/shallow-mpanns 3 (->fakenn))
          mpann-manager-atom (-> smpanns first :mpann-manager-atom)
          mm-forward-ready
          (do (future (mzann/-forward-pass! (first smpanns) [[1.0]]))
              (future (mzann/-forward-pass! (last smpanns) [[2.0]]))
              (mzann/-forward-pass! (second smpanns) [[-1.0]])
              (mzann/-backward-pass! (second smpanns) [:t10] [1.0])
              (mzann/-backward-pass! (first smpanns) [:t00] [1.0])
              @mpann-manager-atom)
          mm-both-ready
          (do (future (mzann/-forward-pass! (first smpanns) [[0.1]]))
              (future (mzann/-forward-pass! (last smpanns) [[0.3]]))
              (mzann/-forward-pass! (second smpanns) [[-0.2]])
              (mzann/-backward-pass! (second smpanns) [:t11] [1.0])
              (mzann/-backward-pass! (first smpanns) [:t01] [1.0])
              (future (mzann/-forward-pass! (first smpanns) [[0.1]]))
              (future (mzann/-forward-pass! (last smpanns) [[0.3]]))
              (mzann/-forward-pass! (second smpanns) [[-0.2]])
              @mpann-manager-atom)]
      (testing "Forward ready, back not ready"
       (is (= (dissoc mm-forward-ready :ann-impl)
              {:nb-smpanns 3
               :current-batch
               {:forward []
                :backward [{:index 1
                            :input-vector [-1.0]
                            :target-distribution :t10
                            :discount-factor 1.0}
                           {:index 0
                            :input-vector [1.0]
                            :target-distribution :t00
                            :discount-factor 1.0}]}
               :current-output-tensor [[1.0] [-1.0] [2.0]]
               :previous-forward-batch [[1.0] [-1.0] [2.0]]})))
     (testing "Both ready"
       (is (= (dissoc mm-both-ready :ann-impl)
              {:nb-smpanns 3
               :current-batch
               {:forward []
                :backward [{:index 1
                            :input-vector [-0.2]
                            :target-distribution :t11
                            :discount-factor 1.0}]}
               :current-output-tensor [[0.1] [-0.2] [0.3]]
               :previous-forward-batch [[0.1] [-0.2] [0.3]]}))
       (is (= (-> mm-both-ready :ann-impl :fake-backprop)
              [:t00 :t01 :t10]))))))

(defn- run-smpann
  [smpann rng]
  (binding [g/*rnd* rng]
    (let [input-vector (vec (repeatedly 32 #(mzc/scale-float (g/float) -1.0 5.0)))
          target-distr (vec (repeatedly 4 g/float))
          ;; one chance on two to have a backward pass
          backward-pass? (g/boolean)]
      (cond-> smpann
        backward-pass?
        (mzann/backward-pass! [target-distr])
        true
        (mzann/forward-pass! [input-vector])))))

(defn- run-once [rngs smpanns]
  (->> (mapv #(future (run-smpann %1 %2)) smpanns rngs)
       (mapv deref)))

(defn- run-all-smpanns [smpanns nb-times seed]
  (let [rngs (map #(java.util.Random. %) (mzr/seeded-seeds seed 0 (count smpanns)))]
    (-> (iterate (partial #'run-once rngs) smpanns)
        (nth nb-times))))

(deftest shallow-mpanns-consistency-and-determinism
  :unstrumented
  (let [seed 40
        layers (mzn/new-layers [32 32 4] mzi/angle-sparse-weights seed)
        underlying-ann
        #(mzann/initialize (mzni/->NeanderthalImpl)
                          layers
                          {:act-fns mza/usual
                           :label-distribution-fn mzld/ansp})
        smpanns #(sut/shallow-mpanns 4 (underlying-ann))
        fn-counts (atom {:fwd 0 :bkd 0})
        inc-if-underlying
        (fn [pass-type ann-impl]
          (when (:layers ann-impl) (swap! fn-counts update pass-type inc))
          ann-impl)]
    (testing "Consistency: Checks that calls to fwd/bkw-pass! of the
    underlying network happen the correct number of times (concurrency
    makes this non-trivial)."
      (with-redefs [mzann/forward-pass!
                    (comp (partial inc-if-underlying :fwd)
                          mzann/forward-pass!)
                    mzann/backward-pass!
                    (comp (partial inc-if-underlying :bkd)
                          mzann/backward-pass!)]
        (run-all-smpanns (smpanns) 200 seed)
        ;; forward = real forward + backward (counted separately at 245)
        (is (= @fn-counts {:fwd 200 :bkd 95}))))
    (testing "Determinism : 2 runs end up with the same output"
      (is (= (-> (run-all-smpanns (smpanns) 200 seed)
                 first :mpann-manager-atom deref :current-output-tensor)
             (-> (run-all-smpanns (smpanns) 200 seed)
                 first :mpann-manager-atom deref :current-output-tensor))))))
