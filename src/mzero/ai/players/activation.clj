(ns mzero.ai.players.activation
  "Details in arch-major/arch minor.

  Warning: for performance, lots of computations alter
  matrices/vectors in place."
  (:require [uncomplicate.neanderthal
             [core :as nc]
             [native :as nn]
             [vect-math :as nvm]
             [random :as rnd]]
            [clojure.spec.alpha :as s]
            [mzero.ai.players.common :refer [ones]]

            [mzero.ai.players.network :as mzn]))


(defn- weighted-pattern-distance-matrix!
  "Compute the layers' *weighted pattern distance matrix*, in
  `working-matrix`, from the input vector with each neuron represented
  by a col of matrices `weights` and `patterns`."
  ([inputs patterns weights working-matrix]
   (let [substract-inputs-to-cols!
         #(nc/rk! -1 inputs (ones (nc/ncols weights)) %)]
     (-> (nc/copy! patterns working-matrix)
         substract-inputs-to-cols!
         nvm/abs!
         (nvm/mul! weights))))
  ([{:as layer,
     :keys [::mzn/inputs ::mzn/patterns ::mzn/weights ::mzn/working-matrix]}]
   (weighted-pattern-distance-matrix! inputs patterns weights working-matrix)
   layer))

(defn- weight-normalization!
  "Divide each element of `working-matrix` by sum of weights of the
  element's column. `working-matrix` is changed"
  ([weights working-matrix]
   (let [normalize!
         (fn [weight-col wm-col] (nc/scal! (/ (nc/sum weight-col)) wm-col))]
     (doall (map normalize! (nc/cols weights) (nc/cols working-matrix)))
     working-matrix))
  ([{:as layer, :keys [::mzn/weights ::mzn/working-matrix]}]
   (weight-normalization! weights working-matrix)
   layer))

(defn- unactivated-outputs!
  "Compute outputs from `working-matrix` before activating them with IOMR,
  by summing every column of `working-matrix`. `outputs` is changed."
  ([working-matrix outputs]
   (->> (nc/scal! 0 outputs)
        (nc/mv! (nc/trans working-matrix) (ones (nc/mrows working-matrix)))))
  ([{:as layer, :keys [::mzn/working-matrix ::mzn/outputs]}]
   (unactivated-outputs! working-matrix outputs)
   layer))

(defn- iomr-activation!
  "Computes inversed-offsetted max-relu activation function, see
  arch-major/minor"
  [outputs]
  (let [s 0.2 ;; seuil d'activation
        decrease-factor (- (/ (- 1.0 s) s))
        f-values ;; see f in arch-minor
        (nc/axpby! (ones (nc/dim outputs)) decrease-factor outputs)
        threshold-factor ;; 0 if < s, 1 if >= s, s/1000 ensures the >= is respected
        #(nvm/relu! (nvm/ceil! (nc/axpy (- (/ s 1000) s) (ones (nc/dim %)) %)))]
    
    (nvm/mul! f-values (threshold-factor f-values))))

(s/fdef forward-pass!
  :args (-> (s/cat :layers ::mzn/layers
                   :inputs (s/every ::mzn/neural-value))
            (s/and (fn [{:keys [inputs layers]}]
                     (comment "Input dimension fits first layer")
                     (= (count inputs) (nc/dim (::mzn/inputs (first layers)))))))
  :ret ::mzn/layers)

(defn forward-pass!
  "Run the network of `layers`. Return the `outputs` of the last
  layer. Almost everything in `layers` is changed.

  The pass has 2 steps:

  - First step uses layers' inputs to compute the working matrix: inputs
  are read but not written to;

  - Second step uses the working matrix to compute
  outputs (i.e. inputs of next layer): inputs are written to but not
  read."
  [layers inputs]
  (nc/transfer! inputs (-> layers first ::mzn/inputs))
  (doall (pmap (comp weight-normalization! weighted-pattern-distance-matrix!)
               layers))
  (last (pmap (comp iomr-activation! ::mzn/outputs unactivated-outputs!)
              layers)))
