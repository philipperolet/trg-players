(ns mzero.ai.players.activation
  "Details in arch-major/arch minor.

  Warning: for performance, lots of computations alter
  matrices/vectors in place."
  (:require [uncomplicate.neanderthal
             [core :as nc]
             [vect-math :as nvm]]
            [clojure.spec.alpha :as s]
            [mzero.ai.players.common :refer [ones zeros-matr zeros]]
            [mzero.ai.players.network :as mzn]))

(def s "Activation threshold" 0.2)
(def decrease-factor (- (/ (- 1.0 s) s)))

(defn- omr!
  "Computes offsetted max-relu activation function, see
  arch-major/minor"
  [outputs]
  (let [dim (nc/dim outputs)
        nullify-if-lower-than-s
        #(nvm/mul! % (nvm/ceil! (nc/axpy (- (* 0.999 s)) (ones dim) %)))]    
    (-> outputs
        (nvm/fmin! (ones dim))
        (nvm/fmax! (zeros dim))
        ;; only keep values above or equal to s
        nullify-if-lower-than-s)))

(s/fdef simultaneous-forward-pass!
  :args (-> (s/cat :layers ::mzn/layers
                   :inputs (s/every ::mzn/neural-value))
            (s/and (fn [{:keys [inputs layers]}]
                     (comment "Input dimension fits first layer")
                     (= (count inputs) (nc/dim (::mzn/inputs (first layers)))))))
  :ret ::mzn/layers)

(defn sequential-forward-pass!
  "Run the network of `layers`. Return the `outputs` of the last
  layer. Almost everything in `layers` is changed.

  This pass is deemed sequential because layers are computed in turn
  from first to last, with each layer using the new value of its
  previous layer's output, rather than being computed all at once,
  using their previous layer's old output."
  [layers inputs]
  (nc/transfer! inputs (-> layers first ::mzn/inputs))
  (doseq [layer layers] (nc/scal! 0 (-> layer ::mzn/outputs)))
  (let [layer-pass
        (fn [{:keys [::mzn/inputs ::mzn/weights ::mzn/outputs] :as layer}]
          (-> (nc/mv! (nc/trans weights) inputs outputs)
              omr!))]
    (last (map layer-pass layers))))
