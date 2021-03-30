(ns mzero.ai.players.motoneurons
  "Module to perform actions given the network output.

  A single output value is a `motoneuron`, controlling a single action
  of the player.

  The network has 4 motoneurons, one for each movement in each direction.

  The main function, `next-direction`, computes the direction to move
  to given an output vector."
  (:require [clojure.spec.alpha :as s]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]
            [mzero.ai.players.network :as mzn]
            [clojure.spec.gen.alpha :as gen]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.random :as rnd]
            [mzero.utils.utils :as u]))

(def motoneuron-number 4)

(s/def ::motoneurons (s/every ::mzn/neural-value :count motoneuron-number))

(defn plug-motoneurons
  "Plug motoneurons to the network describe by `layers`.
  Add and setup new layers to `layers` so that motoneurons are properly
  connected.
  Motoneurons have a specific layer setup, see arch docs for details."
  [layers ndt-rng]
  (-> layers
      (mzn/append-layer motoneuron-number (partial rnd/rand-uniform! ndt-rng))
      (update-in [(count layers) ::mzn/patterns] #(nc/scal! 0 %))))

(s/fdef next-direction
  :args (s/cat :rng (-> (partial instance? java.util.Random)
                        (s/with-gen #(gen/return (java.util.Random.))))
               :motoneurons ::motoneurons)
  :ret ::ge/direction)

(defn next-direction
  "Next direction is chosen by picking randomly among neurons whose
  value is one."
  [rng motoneurons]
  (binding [g/*rnd* rng]
    (u/weighted-rand-nth ge/directions (map #(Math/floor %) motoneurons))))
