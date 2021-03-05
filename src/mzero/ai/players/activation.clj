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
            [mzero.ai.players.common :refer [values-in? ones]]
            [mzero.ai.players.senses :as mzs]
            [clojure.spec.gen.alpha :as gen]))

(s/def ::weights
  (-> nc/matrix?
      (s/and #(values-in? % 0.0 Float/MAX_VALUE)
             (fn [m]
               (comment "At least a non-0 val per neuron (column)")
               (every? #(pos? (nc/asum %)) (nc/cols m))))))

(s/def ::patterns
  (-> nc/matrix?
      (s/and #(values-in? % 0.0 1.0))))

(s/def ::working-matrix (-> nc/matrix?
                            (s/and #(values-in? % 0.0 Float/MAX_VALUE))))

(s/def ::inputs (-> nc/vctr?
                    (s/and #(values-in? % 0.0 1.0))))

(s/def ::outputs ::inputs)

(s/def ::layer
  (-> (s/keys :req [::weights ::patterns ::inputs ::outputs ::working-matrix])
      (s/and
       (fn [{:keys [::weights ::patterns ::inputs ::outputs ::working-matrix]}]
         (comment "Dimensions fit")
         (and (= (nc/dim weights) (nc/dim patterns) (nc/dim working-matrix))
              (= (nc/mrows weights) (nc/mrows patterns) (nc/mrows working-matrix))
              (= (nc/dim inputs) (nc/mrows weights))
              (= (nc/dim outputs) (nc/ncols weights)))))))

(s/def ::layers
  (-> (s/every ::layer)
      (s/and
       (fn [layers]
         (comment "Each layer's output is the next layer's input")
         (reduce #(if (identical? (-> %1 ::outputs) (-> %2 ::inputs)) %2 false)
                 layers)))))

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
  ([{:as layer, :keys [::inputs ::patterns ::weights ::working-matrix]}]
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
  ([{:as layer, :keys [::weights ::working-matrix]}]
   (weight-normalization! weights working-matrix)
   layer))

(defn- unactivated-outputs!
  "Compute outputs from `working-matrix` before activating them with IOMR,
  by summing every column of `working-matrix`. `outputs` is changed."
  ([working-matrix outputs]
   (->> (nc/scal! 0 outputs)
        (nc/mv! (nc/trans working-matrix) (ones (nc/mrows working-matrix)))))
  ([{:as layer, :keys [::working-matrix ::outputs]}]
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

(def max-layer-size 10000)
(def max-test-layer-size 10)

(s/def ::layer-dimension (-> (s/int-in 1 max-layer-size)
                             (s/with-gen #(s/gen (s/int-in 1 max-test-layer-size)))))

(s/fdef new-layers
  ;; Custom-made test of a valid neanderthal `rng`, since there is no
  ;; available `rng?` function in the neanderthal lib
  :args (s/cat :rng (-> #(rnd/rand-uniform! % (nn/dv 1))
                        (s/with-gen #(gen/return (rnd/rng-state nn/native-float))))
               
               :dimensions (s/every ::layer-dimension :min-count 2))
  :ret ::layers
  :fn (fn [{{:keys [dimensions]} :args, layers :ret}]
        (comment "Generated network dimensions fit supplied arguments")
        (every? (fn [[dim {:keys [::inputs]}]] (= (nc/dim inputs) dim))
                (map vector dimensions layers))))

(defn new-layers
  "Initialize a network with connected layers.
  
  `rng` is the random number generator to initialize weights and
  patterns (uniformly in-between 0 & 1).  `dimensions` is the list of
  number of units in the layers; elt 0 is the input dimension, elt 1
  the number of units in the first layer, etc."
  [rng dimensions]
  {:pre [(>= (count dimensions) 2)]}
  (let [[input-dim first-dim & next-dims] dimensions
        new-unplugged-layer
        #(hash-map ::inputs nil
                   ::weights (rnd/rand-uniform! rng (nn/dge %1 %2))
                   ::patterns (rnd/rand-uniform! rng (nn/dge %1 %2))
                   ::working-matrix (nn/dge %1 %2)
                   ::outputs (nn/dv %2))

        first-layer
        (-> (new-unplugged-layer input-dim first-dim)
            (assoc ::inputs (nn/dv input-dim)))

        append-layer
        (fn [layers new-dim]
          (-> (new-unplugged-layer (nc/dim (::outputs (last layers))) new-dim)
              (assoc ::inputs (::outputs (last layers)))
              (#(conj layers %))))]
    
    (reduce append-layer [first-layer] next-dims)))

(s/fdef forward-pass!
  :args (-> (s/cat :layers ::layers
                   :inputs (s/every ::mzs/sense-value))
            (s/and (fn [{:keys [inputs layers]}]
                     (comment "Input dimension fits first layer")
                     (= (count inputs) (nc/dim (::inputs (first layers)))))))
  :ret ::layers)

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
  (nc/transfer! inputs (-> layers first ::inputs))
  (doall (pmap (comp weight-normalization! weighted-pattern-distance-matrix!)
               layers))
  (last (pmap (comp iomr-activation! ::outputs unactivated-outputs!)
              layers)))
