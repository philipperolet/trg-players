(ns mzero.ai.ann.losses
  (:require [mzero.ai.ann.label-distributions :as mzld]))

(defn cross-entropy-loss-gradient
  "Compute the derivative of the loss wrt final output neurons' values,
  a.k.a. `motoneurons-tensor`, assuming class probabilities that the
  ANN outputs are computed by applying `label-distribution-fn` to
  `motoneurons`, with the real class distribution given by
  `target-distribution-tensor`. A `nil` value in a target distribution
  vector indicates that backprop should not occur on this output
  neuron--i.e. it forces a null gradient.Details of cross entropy loss
  derivative computation are [here](doc/cel-derivations.jpg)
  and [here](doc/cel-normalized-relin-softplus.jpg)"
  [label-distribution-fn motoneurons-tensor target-distribution-tensor]
  (let [gradient-scaling-fn (mzld/gradient-scaling-factor label-distribution-fn)
        substract-or-cancel-nil
        (fn [label-distribution target-distribution-vector]
          (map #(if (nil? %2) 0.0 (- %1 %2))
               label-distribution
               target-distribution-vector))
        scale-by-gradient-factor
        (fn [proba-vect mn-vect]
          (map (fn [motoneuron proba-value]
                 (* (gradient-scaling-fn motoneuron) proba-value))
               mn-vect proba-vect))]
    (-> (fn [motoneurons-vector target-distribution-vector]
           (-> (label-distribution-fn motoneurons-vector)
               (substract-or-cancel-nil target-distribution-vector)
               (scale-by-gradient-factor motoneurons-vector)))
        (map motoneurons-tensor target-distribution-tensor))))

(defn mse-loss-gradient
  [labels-tensor targets-tensor]
  (let [mse-loss-vector
        (fn [label-vector target-vector]
          (mapv #(if (nil? %2) 0.0 (- %1 %2)) label-vector target-vector))]
    (mapv mse-loss-vector labels-tensor targets-tensor)))
