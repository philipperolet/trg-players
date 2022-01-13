(ns mzero.ai.ann.neanderthal-impl
  "Implementation of ANN propagation using pure clojure & neanderthal.
  Backprop follows
  https://fleuret.org/dlc/materials/dlc-handout-3-6-backprop.pdf

  When making a forward pass, the size of the batch is stored in
  `current-batch-size`"
  (:require [clojure.spec.alpha :as s]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.common :as mzc]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.network :as mzn]
            [uncomplicate.commons.core :refer [release Releaseable with-release]]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.vect-math :as nvm]
            [uncomplicate.neanderthal.native :as nn]))

(s/def ::weights
  (-> nc/matrix?
      (s/and (mzc/per-element-spec float?)
             #(s/valid? ::mzn/dimension (nc/mrows %))
             #(s/valid? ::mzn/dimension (nc/ncols %))
             (fn [m]
               (comment "At least a non-0 val per neuron (row)")
               (every? #(pos? (nc/asum %)) (nc/rows m))))))

(s/def ::inputs (-> nc/matrix?
                    (s/and (mzc/per-element-spec ::mzn/neural-value)
                           #(s/valid? ::mzn/dimension (nc/dim %)))))

(s/def ::raw-outputs (-> nc/matrix?
                         (s/and (mzc/per-element-spec float?)
                                #(s/valid? ::mzn/dimension (nc/dim %)))))

(s/def ::outputs ::inputs)

(s/def ::raw-output-gradients ::raw-outputs)

(s/def ::layer
  (-> (s/keys :req [::weights ::inputs ::outputs ::raw-outputs ::raw-output-gradients])
      (s/and
       (fn [{:keys [::weights ::inputs ::outputs ::raw-outputs ::raw-output-gradients]}]
         (comment "Dimensions fit")
         (and (= (nc/mrows inputs) (nc/mrows weights))
              (= (nc/ncols weights)
                 (nc/mrows raw-outputs)
                 (nc/mrows outputs)
                 (nc/mrows raw-output-gradients)))))))

(s/def ::layers
  (-> (s/every ::layer :max-count mzn/max-layer-depth)
      (s/and
       (fn [layers]
         (comment "Each layer's output is the next layer's input")
         (reduce #(if (identical? (-> %1 ::outputs) (-> %2 ::inputs)) %2 false)
                 layers)))))

(defn- tensor-fit
  "Return a submatrix of `tensor1` that fits `batch-size` in terms of number
  of columns. For activations and inputs with variable batch size."
  [tensor batch-size]
  (nc/submatrix tensor (nc/mrows tensor) batch-size))

(defn- wtimesx!
  [weights inputs raw-outputs plusb?]
  (let [;; hack to set an individual vector coordinate to 1.0
        ;; on gpu, cannot set indiv vector coordinates
        ;; so we perform a copy of subvector of size one
        ;; containing 1.0
        add1-at-end
        (fn [input]
          (nc/entry! (nc/row input (dec (nc/mrows input))) 1)
          input)]
    (nc/mm! 1.0 weights
            (cond-> inputs plusb? add1-at-end)
            0.0 raw-outputs)))

(defn- batch-size-layers
  "Return layers whose activation fields (inputs, raw-outs, outs, rogs)
  are dimensioned to the given batch size. The dimensioning is done
  with neanderthal views and does not entail any actual data resizing"
  [layers batch-size]
  (let [batch-size-layer
        (fn [layer]
          (reduce #(update %1 %2 tensor-fit batch-size)
                  layer
                  [::inputs ::raw-outputs ::raw-output-gradients ::outputs]))]
    (map batch-size-layer layers)))

(defn sequential-forward-pass!
  "Forward pass: run the network of `layers` using an initial
  `input-tensor`. Almost everything in `layers` is changed.

  For each layers it will compute weights * input and run the
  activation function. If called with flag `plusb?` on, before
  processing a layer, it will reset its input's last element to
  1. This allows computation of `w*x+b` rather than `w*x`, provided
  the rest of the code is aware that inputs' last elements are
  dedicated to this use.

  This pass is called sequential because layers are computed in turn
  from first to last, with each layer using the new value of its
  previous layer's output, rather than being computed all at once,
  using their previous layer's old output."
  [{:as ann-impl :keys [layers] {:keys [::mzann/af!]} :act-fns} input-tensor plusb?]
  (let [current-batch-size (count input-tensor)
        batch-sized-layers (batch-size-layers layers current-batch-size)
        forward-pass-on-layer!
        (fn [{:as layer :keys [::outputs ::raw-outputs ::weights ::inputs]}]
          (wtimesx! weights inputs raw-outputs plusb?)
          (af! ann-impl raw-outputs outputs))]
    (nc/transfer! input-tensor (-> batch-sized-layers first ::inputs))
    (doall (map forward-pass-on-layer! batch-sized-layers))
    (assoc ann-impl :current-batch-size current-batch-size)))

(defn- compute-intermediate-layer-gradients
  "Compute gradients of `next-layer` given `prev-layers` whose gradients
  have already been computed (only the 1st is useful). Return layers
  seq with next-layer cons'ed"
  [ann-impl prev-layers {:as next-layer :keys [::raw-outputs]}]
  (let [af-deriv! (-> ann-impl :act-fns ::mzann/af-deriv!)
        {:keys [::raw-output-gradients ::weights]} (first prev-layers)
        compute-raw-output-gradients
        #(-> (nc/mm! 1.0 (nc/trans weights)
                      raw-output-gradients
                      0.0 %)
             (nvm/mul! (af-deriv! ann-impl raw-outputs)))]
    (-> next-layer
        (update ::raw-output-gradients compute-raw-output-gradients)
        (cons prev-layers))))

(defn- compute-last-layer-gradients
  "Compute gradients for last layer (derivative of
  loss). See [there](doc/ce-loss-deriv.pdf)
  and [there](doc/ce-loss-multiclass.pdf)."
  [{:as last-layer :keys [::raw-outputs]}
   target-distribution-tensor
   loss-gradient-fn]
  (update last-layer ::raw-output-gradients
          (fn [rog]
            (-> raw-outputs
                mzc/tens->vec
                (loss-gradient-fn target-distribution-tensor)
                (nc/transfer! rog)))))

(defn- compute-gradients!
  [{:as ann-impl :keys [loss-gradient-fn current-batch-size]}
   target-distribution-tensor]  
  (let [compute-last-layer-gradients
        (fn [layers]
          (-> (compute-last-layer-gradients (first layers)
                                            target-distribution-tensor
                                            loss-gradient-fn)
              (cons (rest layers))))
        reduce-layer
        (fn [prev-layers next-layer]
          (compute-intermediate-layer-gradients ann-impl prev-layers next-layer))
        compute-other-layers-gradients
        (fn [layers]
          (reduce reduce-layer (vector (first layers)) (rest layers)))]
    (-> ann-impl :layers reverse ;; backprop starts form last layer & goes to first
        (batch-size-layers current-batch-size)
        compute-last-layer-gradients
        compute-other-layers-gradients)))

(defn- update-weights!
  [layers discount-factor-matrix]
  (let [update-layer-weights
        (fn [{:as layer :keys [::raw-output-gradients ::inputs]}]
          ;; if discount factor matrix is identity ignore it
          (when (not-every? #(= % 1.0) discount-factor-matrix)
            (throw (ex-info
                    "Discount factor not implemented,  1.0-valued vector required"
                    {:discount discount-factor-matrix}))
            (nc/mm! raw-output-gradients discount-factor-matrix))
          (update layer ::weights #(nc/mm! (- mzann/step-size)
                                           raw-output-gradients
                                           (nc/trans inputs)
                                           %)))]
    (mapv update-layer-weights layers)))

(defn- convert-layer-without-inputs
  [{:as layer :keys [::mzn/weights]} factory]
  ;; NB: nc/ge understands a nested vectors source as a list of cols 
  (let [output-dim (count (first weights))]
    {::weights (nc/ge factory weights)
     ::raw-outputs (nc/ge factory output-dim mzann/max-batch-size)
     ::raw-output-gradients (nc/ge factory output-dim mzann/max-batch-size)
     ::outputs (nc/ge factory output-dim mzann/max-batch-size)}))

(defn- convert-to-ndt-layers
  [layers factory]
  (let [input-dim (-> layers first ::mzn/inputs count)
        converted-layers
        (map #(convert-layer-without-inputs % factory) layers)
        initial-layer
        (assoc (first converted-layers)
               ::inputs
               (nc/ge factory input-dim mzann/max-batch-size))
        plug-layers
        (fn [already-plugged layer]
          (conj already-plugged
                (assoc layer ::inputs (-> already-plugged last ::outputs))))]
    (reduce plug-layers [initial-layer] (rest converted-layers))))

(defn- computation-setup!
  "Setup the ann for incoming computations. Some computation modes
  require a specific setup, such as CUDA"
  [this]
  (when-let [computation-setup-fn
             (-> this :computation-mode :computation-setup-fn)]
    (computation-setup-fn this)))

(def default-opts {:computation-mode {:type :cpu :factory nn/native-float}})

(defn- diagonal-matrix [ann-impl vector]
  (nc/gd (-> ann-impl :computation-mode :factory) (count vector) vector))

(defrecord NeanderthalImpl []
  mzann/ANN
  (-tens->vec [this ndt-tensor]
    (computation-setup! this)
    (mzc/tens->vec ndt-tensor))

  (-initialize [this layers opts]
    (let [this-with-opts (merge this default-opts opts)
          factory (-> this-with-opts :computation-mode :factory)
          max-sized-matrix #(nc/ge factory mzn/max-dimension mzn/max-dimension)]
      (assoc this-with-opts
             :layers (convert-to-ndt-layers layers factory)
             :ones (nc/entry! (max-sized-matrix) 1)
             :zeros (max-sized-matrix)
             :buffer (max-sized-matrix)
             :current-batch-size nil)))

  (-forward-pass! [this inputs]
    (computation-setup! this)
    (sequential-forward-pass! this inputs true))

  (-backward-pass! [this target-distribution-tensor discount-factor]
    (assert (= (-> this :current-batch-size) (count target-distribution-tensor)))
    (computation-setup! this)
    (-> (compute-gradients! this target-distribution-tensor)
        (update-weights! discount-factor))
    this)
  
  (-backward-pass! [this input-tensor target-distribution-tensor discount-factor]
    (-> (mzann/-forward-pass! this input-tensor)
        (mzann/-backward-pass! target-distribution-tensor discount-factor)))

  (-layer-data [{:as this :keys [layers current-batch-size]} lindex lkey]
    (computation-setup! this)
    (let [weights (-> layers (nth lindex) ::weights)
          mrows (nc/mrows weights) ncols (nc/ncols weights)
          last? (= lindex (dec (count layers)))
          this-ns "mzero.ai.ann.neanderthal-impl"]
      (case lkey ;; weight and bias are in a single matrix ::weights
        "weight" (nc/submatrix weights (dec mrows) (if last? ncols (dec ncols)))
        "bias" (nc/subvector (last (nc/rows weights)) 0 (if last? ncols (dec ncols)))
        (tensor-fit (get-in layers [lindex (keyword this-ns lkey)])
                    current-batch-size))))

  (nb-layers [this] (-> this :layers count))

  (clear-neuron! [this lindex nindex]
    (computation-setup! this)
    (let [weights
          (-> this :layers
              (nth (mod lindex (-> this :layers count)))
              ::weights)]
      (->> (nc/row weights (mod nindex (nc/mrows weights)))
           (nc/scal! 0.0))))

  Releaseable
  (release [this]
    (let [release-layer
          (fn [layer] (doseq [item (vals layer)] (release item)))]
      (doseq [layer (-> this :layers)] (release-layer layer))
      (release (-> this :ones))
      (release (-> this :zeros)))))
