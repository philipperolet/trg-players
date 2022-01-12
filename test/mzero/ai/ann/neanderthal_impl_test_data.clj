(ns mzero.ai.ann.neanderthal-impl-test-data
  "Collection of ANN inputs with expected final outputs of forward &
  backward pass, computed with a known working version of ann
  impl, with params of test-ann-impl below"
  (:require [clojure.data.generators :as g]
            [mzero.ai.ann.activations :as mza]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.neanderthal-impl :as sut]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.ann.losses :as mzl]))

(def layer-dims [16 128 128 16])
(defn test-ann-impl []
  (mzann/initialize (sut/->NeanderthalImpl)
                    (mzn/new-layers layer-dims mzi/random-weights 25)
                    {:act-fns mza/usual
                     :label-distribution-fn mzld/ansp
                     :loss-gradient-fn (partial mzl/cross-entropy-loss-gradient mzld/ansp)}))

(defn- generate-datapoint-for-seed
  [seed]
  (let [test-impl (test-ann-impl)
        last-layer-index (dec (mzann/nb-layers test-impl))
        vect-size (first layer-dims)
        input-vector
        (binding [g/*rnd* (java.util.Random. seed)]
          (vec (repeatedly vect-size g/float)))
        target-distribution
        (binding [g/*rnd* (java.util.Random. seed)]
          (-> (into (repeat (/ vect-size 2) 0.0)
                    (repeat (/ vect-size 2) (/ 2 (double vect-size))))
              g/shuffle vec))
        updated-impl
        (-> test-impl
            (mzann/forward-pass! [input-vector])
            (mzann/backward-pass! [target-distribution]))]
    {:in input-vector
     :out (first (mzann/layer-data updated-impl last-layer-index "outputs"))
     :target target-distribution
     :updated-first-weights
     (mzann/tens->vec updated-impl (-> updated-impl :layers first ::sut/weights))
     :updated-last-weights
     (mzann/tens->vec updated-impl (-> updated-impl :layers last ::sut/weights))}))

(def backward-pass-test-data-file
  "test/mzero/ai/ann/neanderthal_impl_test_data.data")

(defn- generate-test-data!
  "Generate the data used in tests for forward and backward pass
  validity. Should only be used with *known working version* when
  there is a *need* to re-generate the data

  In = input fed to network, out = the output it should provide,
  target = a target distribution, updated-first/last-weights : the
  expected weights after the network updates in first and last layer."
  []
  (binding [*print-length* (apply max layer-dims)]
    (->> (vec (map generate-datapoint-for-seed (range 4)))
         (spit backward-pass-test-data-file))))

(def test-data
  (read-string (slurp backward-pass-test-data-file)))
