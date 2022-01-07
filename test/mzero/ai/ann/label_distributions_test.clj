(ns mzero.ai.ann.label-distributions-test
  (:require [clojure.test :refer [are is]]
            [mzero.ai.ann.label-distributions :as sut]
            [mzero.utils.testing :refer [deftest]]
            [mzero.utils.utils :as u]))

(def softmax-result
  [
   [4.539635483524762E-5
    0.9999212026596069
    1.670038363954518E-5
    1.670038363954518E-5
    0.0]
   [0.8572205901145935
    0.09498275071382523
    0.012854519300162792
    0.03494219854474068
    1.401298464324817E-45]
   [3.0720966606168076E-6
    0.49999845027923584
    0.49999845027923584
    1.8216880036222622E-44
    1.8216880036222622E-44]
   [2.2363195739671937E-7
    6.078946626075776E-7
    1.6524290913366713E-6
    0.2689407467842102
    0.7310567498207092]
   [0.0 0.0 0.0 1.0]])

(deftest softmax
  (let [test-matr
        [[0.0 10.0 -1.0 -1.0 -100.0]
         [3.2 1.0 -1.0 0.0 -100.0]
         [-12.0 0.0 0.0 -100.0 -100.0]
         [-3.0 -2.0 -1.0 11.0 12.0]
         [1.0 1.0 0.0 1.0E20]]
        res-ndt
        (sut/softmax test-matr)]
    (doseq [i (range (count test-matr))]
      (is (u/coll-almost= (#'sut/softmax (nth test-matr i))
                          (nth softmax-result i)
                          0.0001)))
    (doseq [[x y] (map vector softmax-result res-ndt)]
      (is (u/coll-almost= x y 0.0001)))))

(deftest ^:deprecated normalized-relinear
  (are [args res] (u/coll-almost= (sut/normalized-relinear args) res)
    [0.0 0.0 1.0 0.0] [0.0 0.0 1.0 0.0]
    [1 2 3 4] [0.1 0.2 0.3 0.4]
    [-1 1 2 3 4] [0.0 0.1 0.2 0.3 0.4]
    [1.0 0.0 -3.1 1.5 2.5] [0.2 0.0 0.0 0.3 0.5]
    [0.0 0.0] [0.5 0.5]
    [-1.0 -2.0 -3.1 0.0] (repeat 4 0.25)))

(deftest ansp
  (are [args res] (u/coll-almost= (map float (sut/ansp args)) res)
    [-120.0 -120.0 -20.1 -0.3] [0.0 0.0 3.3642847E-9 1.0]
    [100 200 300 400] [0.1 0.2 0.3 0.4]
    [-120 100 200 300 400] [0.0 0.1 0.2 0.3 0.4]
    [0.0 0.0] [0.5 0.5]
    [(Math/log 4) -20.1 0.0]
    (map #(/ % (Math/log 10)) [(Math/log 5) (* (Math/log 10) 8.099631E-10) (Math/log 2)] )))

