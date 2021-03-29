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

(defn- pattern-distance-matrix!
  "Compute the layers' *weighted pattern distance matrix*, in
  `working-matrix`, from the input vector with each neuron represented
  by a col of matrix `patterns` (and weights, not used here)."
  ([inputs patterns working-matrix]
   (let [substract-inputs-to-cols!
         #(nc/rk! -1 inputs (ones (nc/ncols patterns)) %)]
     (-> (nc/copy! patterns working-matrix)
         substract-inputs-to-cols!
         nvm/abs!)))
  ([{:as layer,
     :keys [::mzn/inputs ::mzn/patterns ::mzn/working-matrix]}]
   (pattern-distance-matrix! inputs patterns working-matrix)
   layer))

(def s "Activation threshold" 0.2)
(def decrease-factor (- (/ (- 1.0 s) s)))

(defmulti proximity-matrix!
  "Computes the proximity matrix in-place given a pattern distance
  matrix `working-matrix`"
  #(if (::mzn/working-matrix %) :layer :working-matrix))

(defmethod proximity-matrix! :working-matrix
  [working-matrix]
  (let [m (nc/mrows working-matrix) n (nc/ncols working-matrix)]
    (->> working-matrix
         (nc/scal! decrease-factor)
         (nc/rk! (ones m) (ones n))
         (#(nvm/fmax! % (zeros-matr m n))))))

(defmethod proximity-matrix! :layer [l] (proximity-matrix! (::mzn/working-matrix l)))

(defn- unactivated-outputs!
  "Compute outputs from `working-matrix` before activating them with OMR,
  by doing a `weights`ed sum of every column of
  `working-matrix`. `outputs` is changed."
  ([working-matrix weights outputs]
   (-> (map nc/dot (nc/cols weights) (nc/cols working-matrix))
       (nc/transfer! outputs)))
  ([{:as layer, :keys [::mzn/working-matrix ::mzn/weights ::mzn/outputs]}]
   (unactivated-outputs! working-matrix weights outputs)
   layer))

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
  (doall (pmap (comp proximity-matrix! pattern-distance-matrix!) layers))
  (last (pmap (comp omr! ::mzn/outputs unactivated-outputs!)
              layers)))
