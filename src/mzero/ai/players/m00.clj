(ns mzero.ai.players.m00
  "M0 player, early version. See arbre/version docs."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.players.senses :as mzs]
            [mzero.ai.players.activation :as mza]
            [mzero.ai.players.network :as mzn]
            [mzero.ai.players.motoneurons :as mzm]
            [mzero.game.state :as gs]
            [uncomplicate.neanderthal
             [core :as nc]
             [native :as nn]
             [random :as rnd]]
            [clojure.spec.alpha :as s]
            [clojure.data.generators :as g]))

(s/fdef scale-float
  :args (s/cat :flt (-> (s/double-in 0 1)
                      (s/and float?))
               :low float?
               :hi float?))

(defn- scale-float [flt low hi] (+ low (* flt (- hi low))))

(def neg-weight-ratio "Negative weights ratio" 0.1)

(defn nonzero-weights-nb
  "Number of nonzero weights for a column of size `dim` given a range
  `flt` between 0 and 1"
  ([dim flt]
   (int (inc (* (Math/sqrt dim) (scale-float flt 0.3 1.0)))))
  ([dim] (nonzero-weights-nb dim (g/float))))

(defn- rand-nonzero-vector!
  [dim]
  (let [nzw (nonzero-weights-nb dim)
        
        neg-weights-nb ;; on average, nwr ratio of negative weights 
        (int (Math/round (* nzw (scale-float (g/float) 0.0 (* 2 neg-weight-ratio)))))

        ones-vector
        (into (repeat neg-weights-nb -1) (repeat (- nzw neg-weights-nb) 1))
        
        normalization-factor (apply + ones-vector)]
    (->> ones-vector
         (into (repeat (- dim nzw) 0))
         (map #(/ % normalization-factor))
         g/shuffle)))

(defn- sparse-weights
  "Create a sparse matrix of size `m`*`n`, aimed to be used as weights.

  Every neuron (= column) has about sqrt(#rows)/2 non-zero weights,
  with a random ratio of negative weights averaging to
  `neg-weight-ratio`, all initialized to the same value (in ]0,1])
  such that they sum to one."
  [m n rng]
  (binding [g/*rnd* rng]
    (nn/fge m n (repeatedly n #(rand-nonzero-vector! m)))))

(defn- create-ndt-rng
  "Neanderthal needs its own rng-state in addition to the player's
  regular rng"
  [player-opts]
  (if-let [seed (:seed player-opts)]
    (rnd/rng-state nn/native-float seed)
    (rnd/rng-state nn/native-float)))

(defn- reset-b-values
  "Set the last value of each layer's input vector to 1.

  Therefore, the last value of a layer's input represents the `b` in
  perceptron computation `w.x+b`"
  [layers]
  (doseq [layer-input (map ::mzn/inputs layers)]
    (nc/entry! layer-input (dec (nc/dim layer-input)) 1.0))
  layers)

(defn- initialize-layers
  "Initialize layers, with inner layers' weights sparsified, so that
  neurons are similar to what they might be when generation starts,
  and patterns randomized so that movements vary (otherwise the same
  direction is always picked).

  Layer and input dimensions are increased of 1 to allow for b in
  w.x+b perceptron formula. Later, propagation ensures it is always
  set to 1."
  [layer-dims input-dim weights-fn]
  (let [dimensions (map inc (cons input-dim layer-dims))]
    (-> (mzn/new-layers dimensions weights-fn)
        (mzm/plug-motoneurons weights-fn)
        mzm/setup-random-move-reflex)))



(defrecord M00Player []
  aip/Player
  (init-player
    [player {:as opts, :keys [layer-dims]} {:as world, :keys [::gs/game-state]}]
    {:pre [(pos? (count layer-dims))
           (s/valid? (s/every ::mzn/dimension) layer-dims)]}
    (let [brain-tau (inc (count layer-dims))
          senses (mzs/initialize-senses! brain-tau game-state (:rng player))
          input-dim (count (::mzs/input-vector senses))
          weights-init! #(sparse-weights %1 %2 (:rng player))
          initial-layers
          (initialize-layers layer-dims input-dim weights-init!)]
      (assoc player :layers initial-layers ::mzs/senses senses)))

  (update-player [player {:as world, :keys [::gs/game-state]}]
    (let [player-forward-pass
          #(mza/sequential-forward-pass! (-> % :layers)
                              (-> % ::mzs/senses ::mzs/input-vector))

          make-move
          #(->> (player-forward-pass %)
                seq
                (mzm/next-direction (:rng player))
                (assoc % :next-movement))]
      
      (-> player 
          (update ::mzs/senses mzs/update-senses world player)
          (update :layers reset-b-values)
          make-move))))
