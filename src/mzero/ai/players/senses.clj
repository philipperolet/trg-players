(ns mzero.ai.players.senses
  "Module to compute player senses given world data at a given time, as
  a float-valued `::senses` vector containing valid neural values (see
  activation.clj).

  Computation of the senses requires the previous senses value,
  as well as a `vision-depth` and the previous `score`, both of which
  are kept in `::senses-data`.

  The module's main function is `udpate-senses-data`, performing the
  senses computation. `initial-senses-data` is also part of the public
  interface.

  Player senses are:
  -  its *vision*, which is a subset of the board cells
  for which it can see the cell contents (such cells are *visible* cells);
  - its `satiety`, which raises when it eats fruits and decreases when
  it doesn't.

  A player's `vision-depth` is the distance up to which it can see a
  cell content relatively to its current position.

  E.g. a player with vision depth 2 in position [3 0] can see cells
  [3 2], [1 0] or [1 2] but not [3 3] or [0 0].

  Therefore, the player can *see* a square matrix of visible cells of
  edge length vision-depth*2 + 1.

  The intention of the senses module is that a player using it should
  not interact with the world in any other way."
  (:require [mzero.game.board :as gb]
            [clojure.spec.alpha :as s]
            [mzero.ai.world :as aiw]
            [mzero.utils.modsubvec :refer [modsubvec]]
            [mzero.game.state :as gs]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.activation :as mza]))

(def minimal-satiety-value 0.04)

(s/def ::satiety (-> ::mza/neural-value
                     (s/and #(or (>= % minimal-satiety-value) (= % 0.0)))
                     (s/with-gen (fn [] (gen/fmap
                                         #(if (< % minimal-satiety-value) 0.0 %)
                                         (s/gen ::mza/neural-value))))))

(defn- satiety [senses] (last senses))

(s/fdef new-satiety
  :args (-> (s/cat :old-satiety ::satiety
                   :previous-score ::gs/score
                   :score ::gs/score))
  :ret ::satiety)

(defn- new-satiety
  "Compute satiety from its former value `old-satiety` and whether the
  score increased at this step. see m0.0.1 notes for explanations."
  [old-satiety previous-score score]
  (let [fruit-eaten-increment (if (< previous-score score) 0.3 0.0)
        decrease-factor 0.95
        new-satiety (+ (* old-satiety decrease-factor) fruit-eaten-increment)]
    (cond
      (< new-satiety minimal-satiety-value) 0.0
      (> new-satiety 1) 1.0
      :else new-satiety)))


(def min-vision-depth 1)
(def max-vision-depth (dec (int (/ gb/max-board-size 2))))

(s/def ::vision-depth (s/int-in 1 max-vision-depth))

(defn visible-matrix-edge-size
  "Edge size of the matrix of visible cells"
  [vision-depth]
  (inc (* vision-depth 2)))

(defn senses-vector-size
  "Size of senses vector = number of visible cells + 1 (satiety)"
  [vision-depth]
  (int (inc (Math/pow (visible-matrix-edge-size vision-depth) 2))))

(defn- senses-vector-spec
  "Return a spec of senses vector fitting vision-depth"
  [vision-depth]
  (let [min-count (senses-vector-size (or vision-depth min-vision-depth))
        max-count (senses-vector-size (or vision-depth max-vision-depth))
        spec-def (s/every ::mza/neural-value
                          :kind vector?
                          :min-count min-count
                          :max-count max-count)
        generator-function
        (fn []
          (gen/fmap #(assoc % (dec (count %)) (gen/generate (s/gen ::satiety)))
                    (s/gen spec-def)))]
    (-> spec-def
        (s/and (fn [sv]
                 (comment "Vector must have a valid satiety")
                 (s/valid? ::satiety (satiety sv))))
        (s/with-gen generator-function))))

(s/def ::senses (senses-vector-spec nil))
(s/def ::previous-score ::gs/score)

(defn- senses-data-generator [vision-depth]
  (gen/hash-map ::previous-score (s/gen ::previous-score)
                ::vision-depth (gen/return vision-depth)
                ::senses (s/gen (senses-vector-spec vision-depth))))

(s/def ::senses-data
  (-> (s/keys :req [::senses ::previous-score ::vision-depth])
      (s/and (fn [{:keys [::vision-depth ::senses]}]
               (comment "Senses vector size depends on vision depth")
               (= (count senses) (senses-vector-size vision-depth))))
      (s/with-gen #(gen/bind (s/gen ::vision-depth) senses-data-generator))))

(defn- visible-matrix  
  [game-board player-position vision-depth]
  (let [size (visible-matrix-edge-size vision-depth)
        offset-row (- (first player-position) vision-depth)
        offset-col (- (second player-position) vision-depth)]
    (->> (modsubvec game-board offset-row size)
         (map #(modsubvec % offset-col size))
         vec)))

(defn- visible-matrix-vector
  "Turn the board subset visible by the player from keyword
  matrix to a real-valued vector.

  Each type of elt on the board has a corresponding float value
  between 0.0 - 1.0, as described below"
  [visible-matrix]
  (->> visible-matrix
       (reduce into [])
       (map {:wall 1.0 :empty 0.0 :fruit 0.5 :cheese 0.2})
       vec))

(defn- update-senses-vector
  [old-senses-vector senses-data game-state]
  (let [{:keys [::previous-score ::vision-depth]} senses-data
        {:keys [::gb/game-board ::gs/player-position ::gs/score]} game-state
        visible-matrix (visible-matrix game-board player-position vision-depth)]
    (conj (visible-matrix-vector visible-matrix)
          (new-satiety (satiety old-senses-vector) previous-score score))))

(defn initial-senses-data
  [vision-depth]
  {::senses (vec (repeat (senses-vector-size vision-depth) 0.0))
   ::vision-depth vision-depth
   ::previous-score 0})

(defn vision-depth-fits-game?
  "`true` iff the vision matrix edge is smaller than the game board edge"
  [vision-depth game-board]
  (<= (visible-matrix-edge-size vision-depth) (count game-board)))

(s/fdef update-senses-data
  :args (-> (s/cat :senses-data ::senses-data
                   :world ::aiw/world-state)
            (s/and (fn [{{:keys [::vision-depth]} :senses-data
                         {{:keys [::gb/game-board]} ::gs/game-state} :world}]
                     (vision-depth-fits-game? vision-depth game-board))))
  :ret ::senses-data)

(defn update-senses-data
  "Compute a new senses-vector using its previous value and the,
  updating score with the previous score"
  [senses-data {:as world, :keys [::gs/game-state]}]
  (-> senses-data
      (update ::senses update-senses-vector senses-data game-state)
      (assoc ::previous-score (game-state ::gs/score))))
