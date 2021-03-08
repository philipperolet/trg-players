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
            [mzero.ai.players.activation :as mza]))

(def motoneuron-number 4)

(s/def ::motoneurons (s/every ::mza/neural-value :count motoneuron-number))

(defn- random-nth-weighted
  "Pick an element of `coll` randomly according to the
  distribution represented by `weights`"
  [coll weights]
  (loop [sum-rest (* (g/double) (apply + weights))
         [item & restc] coll
         [w & restw] weights]
    (if (<= (- sum-rest w) 0) item (recur (- sum-rest w) restc restw))))

(s/fdef next-direction
  :args (s/cat :motoneurons ::motoneurons)
  :ret ::ge/direction)

(defn next-direction
  "Next direction is chosen by considering each motoneuron value as a
  non-normalized probability, and picking randomly according to the
  distribution"
  [motoneurons]
  (random-nth-weighted ge/directions motoneurons))




