(ns mzero.ai.ann.ann
  "Module for the ANN protocol. Options:
   -`:computation-mode` for gpu vs cpu computations;

   -`:act-fns` for the network's activation function & derivative;

   - `::loss-gradient-fn` a function to compute the loss gradient wrt output values;
  
   -`:label-distribution-fn` for computing a label probability distribution
    given motoneuron values."
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.network :as mzn]
            [mzero.utils.utils :as u]))

(def activation-threshold 0.2)
(def step-size 0.005)
(def max-batch-size 128)

(defprotocol ANN
  "Protocol for implementing artificial neural networks in m0, mainly
  propagation (forward/backward pass). ATTOW focused on linear layers with
  bias.

  Output after forward propagation should be accessed via the `output`
  function of this module."
  (-initialize [this layers opts])
  (-forward-pass! [this input-tensor] "Perform the formard pass. Should not
  be accessed directly, but via `forward-pass!`
  (no hyphen) defined below")
  (-backward-pass!
    [this target-distribution-tensor discount-vector]
    [this input-tensor target-distribution-tensor discount-vector]
    "Update weights according to gradient descent performed on a
  cross-entropy loss, given `input-tensor` and
  `target-distribution-tensor` (e.g. [[0.0 0.0 1.0 0.0]] for target
  class 2, or [[0.25 0.25 0.25 0.25]] for an evenly probable
  classification as target. The learning rate will be multiplied by
  `discount-vector`, a vector of discount factors. If only
  `target-distribution-tensor` is provided, the `input-tensor` used
  for the last forward pass should be used (thus no need to run the
  forward pass again)")
  (-tens->vec [this tensor] "Given `tensor` with implem-dependent
  type, return a clojure vector with the same data")
  (nb-layers [this] "Number of layers")
  (-layer-data [this lindex lkey] "Return the `lindex`th layer's
  `lkey` tensor, lkey being a string from `::mzn/layer-elements`")
  (-act-fns [this] "Return the network's activation function &
  derivative, that should be stored as a key in `this` in the form
  {:af! #fn :af-deriv! #fn}")
  (clear-neuron! [this lindex nindex] "Reset neuron nb `nindex` of
  layer nb `lindex` at 0 weights (negative indices should be accepted)")
  (serialize [this] "Return file-writeable data such that if the data
  is read back with `deserialize` into a new ann-impl,`this` and the
  new impl would behave the same, that is, forward / backward passes
  on same inputs would yield same outputs")
  (deserialize [this data] "See `serialize` above"))

(s/def ::af! fn?)
(s/def ::af-deriv! fn?)
(s/def ::act-fns (s/keys :req [::af! ::af-deriv!]))

(s/def ::type #{:gpu-opencl :gpu-cuda :cpu})
(s/def ::computation-mode (s/keys :req-un [::type]))
(s/def ::ann-impl-name string?)

(s/def ::loss-gradient-fn fn?)

(s/def ::ann-opts
  (s/or :opts-map (s/keys :opt-un [::loss-gradient-fn
                                   ::ann-impl-name
                                   ::computation-mode
                                   ::act-fns
                                   ::mzld/label-distribution-fn])
        :nil nil?))

(s/def ::ann #(satisfies? ANN %))

(s/fdef initialize
  :args (s/cat :this ::ann
               :layers ::mzn/layers
               :opts ::ann-opts)
  :ret ::ann)

(defn initialize
  "Should return an initialized implem of ANN"
  [this layers opts]
  (-initialize this layers opts))

(s/fdef layer-data
  :args (-> (s/cat :ann-impl ::ann
                   :lindex (s/int-in 0 mzn/max-layer-depth)
                   :lkey mzn/layer-elements))
  :ret vector?)

(defn layer-data [ann-impl lindex lkey]
  (-tens->vec ann-impl (-layer-data ann-impl lindex lkey)))

(defn tens->vec [ann-impl tensor]
  (-tens->vec ann-impl tensor))

(defn output
  "Final output of the last forward pass."
  [ann-impl]
  (layer-data ann-impl (dec (nb-layers ann-impl)) "raw-outputs"))

(defn batch-of [spec]
  (-> (s/every spec)
      (s/and (fn [elts]
               (comment "All elts of batch are of same size")
               (every? #(= (count %) (count (first elts))) elts)))))

(s/fdef forward-pass!
  :args (-> (s/cat :ann-impl #(satisfies? ANN %)
                   :input-tensor (batch-of ::mzn/inputs)))
  :ret (s/and #(satisfies? ANN %)))

(defn forward-pass!
  [ann-impl input-tensor]
  (-forward-pass! ann-impl input-tensor))

;; does not necessarily sum to one due to keep-nil-move-at-zero
(s/def ::target-distribution (s/every ::mzn/neural-value))

(s/fdef backward-pass!
  :args (s/alt
         :default
         (s/cat :ann-impl #(satisfies? ANN %)
                :target-distribution-tensor (batch-of ::target-distribution))
         :with-discount
         (-> (s/cat :ann-impl #(satisfies? ANN %)
                    :target-distribution-tensor (batch-of ::target-distribution)
                    :discount-vector (s/every float?))
             (s/and (fn [{:keys [target-distribution-tensor discount-vector]}]
                      (comment "Dimensions fit")
                      (= (count target-distribution-tensor)
                         (count discount-vector)))))
         :full
         (-> (s/cat :ann-impl #(satisfies? ANN %)
                    :input-tensor (batch-of ::mzn/inputs)
                    :target-distribution-tensor (batch-of ::target-distribution)
                    :discount-vector (s/every float?))
             (s/and (fn [{:keys [target-distribution-tensor
                                 discount-vector
                                 input-tensor]}]
                      (comment "Dimensions fit")
                      (= (count target-distribution-tensor)
                         (count discount-vector)
                         (count input-tensor))))))
  :ret (s/and #(satisfies? ANN %)))

(defn backward-pass!
  ([ann-impl target-distribution-tensor discount-vector]
   (-backward-pass! ann-impl target-distribution-tensor discount-vector))
  ([ann-impl target-distribution-tensor]
   (-backward-pass! ann-impl
                   target-distribution-tensor
                   (repeat (count target-distribution-tensor) 1.0)))
  ([ann-impl input-tensor target-distribution-tensor discount-vector]
   (-backward-pass! ann-impl input-tensor target-distribution-tensor discount-vector)))

(defn act-fns [{:as ann-impl :keys [act-fns]}]
  act-fns)
