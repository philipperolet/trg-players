(ns mzero.ai.players.senses
  "Module to compute player senses given world and player data at a
  given time, as a float-valued `::input-vector`containing valid
  neural values (see activation.clj).

  Senses are the part of the player interfacing between the player's
  brain and the rest of the world.

  Player senses are:
  -  its `vision`, which is a subset of the board cells
  for which it can see the cell contents (such cells are *visible* cells);
  - its `satiety`, which raises when it eats fruits and decreases when
  it doesn't;
  - its `motoception`, short for motricity perception, which activates
  when it moves and stays on for a while

  Senses comprise:
  - `::input-vector` fed to the player brain;
  - inial `::params` necessary to compute them;
  - `::data` on the player and the world, updated at each iteration along
  with `::input-vector`.

  The module provide functions `initialize-senses!` and `udpate-senses`.

  See arch minor for more details."
  (:require [mzero.game.board :as gb]
            [clojure.spec.alpha :as s]
            [mzero.utils.modsubvec :refer [modsubvec]]
            [mzero.game.state :as gs]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.network :as mzn]
            [mzero.game.events :as ge]
            [mzero.utils.utils :as u]))

;; Brain time constant
(s/def ::brain-tau (s/int-in 1 200))

(defn- default-persistence [brain-tau] (* 2 brain-tau))

;; Satiety
;;;;;;;;;;

(def minimal-satiety 0.04)

(s/def ::previous-score ::gs/score)

(s/def ::satiety (-> ::mzn/neural-value
                     (s/and #(or (>= % minimal-satiety) (= % 0.0)))
                     (s/with-gen (fn [] (gen/fmap
                                         #(if (< % minimal-satiety) 0.0 %)
                                         (s/gen ::mzn/neural-value))))))

(defn- satiety [senses] (last senses))

(s/fdef new-satiety
  :args (-> (s/cat :old-satiety ::satiety
                   :previous-score ::gs/score
                   :score ::previous-score
                   :brain-tau ::brain-tau))
  :ret ::satiety)

(defn- new-satiety
  "Compute satiety from its former value `old-satiety` and whether the
  score increased at this step. see m0.0.1 arch minor for explanations."
  [old-satiety previous-score score brain-tau]
  (let [fruit-increment 0.3
        satiety-persistence (default-persistence brain-tau)
        decrease-factor (Math/pow (/ minimal-satiety fruit-increment) (/ satiety-persistence))
        fruit-eaten-increment (if (< previous-score score) fruit-increment 0.0)
        new-satiety (+ (* old-satiety decrease-factor) fruit-eaten-increment)]
    (cond
      (< new-satiety minimal-satiety) 0.0
      (> new-satiety 1) 1.0
      :else new-satiety)))

;; Motoception
;;;;;;;;;;;;;;
(def min-motoception-activation 0.95)
(def activation-value 1.0)

(s/def ::last-move (s/or :nil nil?
                        :direction ::ge/direction))

(s/def ::motoception (-> ::mzn/neural-value
                         (s/and #(or (= % 0.0) (>= % min-motoception-activation)))
                         (s/with-gen
                           #(gen/one-of
                             [(s/gen (s/double-in min-motoception-activation activation-value))
                              (gen/return 0.0)]))))

(s/fdef new-motoception
  :args (s/cat :old-motoception ::motoception
               :brain-tau ::brain-tau
               :last-move ::last-move)
  :ret ::motoception)

(defn- new-motoception
  "Compute the motoception value. See arch minor for details.

  To know if motoception should deactivate, i.e. if
  *motoception-persistence* iterations, computed from `brain-tau`,
  occured with no new move from player, without storing previous
  state, the motoception value is decreased from a small increment
  every time. An added benefit is the ability for the player to make
  use of the differences in activation values (v.s. keeping the
  activation value fixed during *motoception-persistence*
  iterations)."
  [old-motoception brain-tau last-move]
  (let [motoception-persistence (default-persistence brain-tau)
        increment
        (-> (- activation-value min-motoception-activation)
            (/ motoception-persistence))
        
        new-motoception
        (- old-motoception increment)]
    (cond
      (some? last-move) activation-value
      (> new-motoception min-motoception-activation) new-motoception
      :else 0.0)))

(defn motoception [senses]
  (nth senses (- (count senses) 2)))

;; Vision
;;;;;;;;;

(def vision-depth 4)
(def visible-matrix-edge-size
  "Edge size of the matrix of visible cells"
  (inc (* vision-depth 2)))

(defn- visible-matrix  
  [game-board player-position]
  (let [offset-row (- (first player-position) vision-depth)
        offset-col (- (second player-position) vision-depth)]
    (->> (modsubvec game-board offset-row visible-matrix-edge-size)
         (map #(modsubvec % offset-col visible-matrix-edge-size))
         vec)))

(def board-cell-to-float-map  {:wall 1.0 :empty 0.0 :fruit 0.5 :cheese 0.2})
(defn- visible-matrix-vector
  "Turn the board subset visible by the player from keyword
  matrix to a real-valued vector.

  Each type of elt on the board has a corresponding float value
  between 0.0 - 1.0, as described below"
  [visible-keyword-matrix]
  (->> visible-keyword-matrix
       (reduce into [])
       (map board-cell-to-float-map)
       vec))

(defn vision-cell-index
  "Return the index of a vision cell at position `[row col]` relative to the
  player in `::input-vector`"
  [[row col]]
  (+ (* (+ row vision-depth) visible-matrix-edge-size) (+ col vision-depth)))

(defn vision-depth-fits-game?
  "`true` iff the vision matrix edge is smaller than the game board edge"
  [game-board]
  (<= visible-matrix-edge-size (count game-board)))

;; Input vector
;;;;;;;;;;;;;;;;

(def input-vector-size
  "Size of senses vector = number of visible cells + 1 (satiety) +
  1 (motoception)"
  (int (+ 2 (Math/pow visible-matrix-edge-size 2))))

(s/def ::input-vector
  (-> (s/every ::mzn/neural-value :kind vector? :count input-vector-size)
      (u/with-mapped-gen
        #(assoc %
                (- input-vector-size 2) (gen/generate (s/gen ::motoception))
                (dec input-vector-size) (gen/generate (s/gen ::satiety))))
      (s/and (fn [sv]
                 (comment "Vector must have a valid satiety")
                 (s/valid? ::satiety (satiety sv)))
               (fn [sv]
                 (comment "Vector must have a valid motoception")
                 (s/valid? ::motoception (motoception sv))))))

(s/def ::params (s/keys :req [::brain-tau]))
(s/def ::data (s/keys :req [::previous-score ::gs/game-state ::last-move]))

(defn- update-data
  "Updates the senses data given player & world"
  [{:as old-data, {:keys [::gs/score]} ::gs/game-state}
   {:as world, :keys [::gs/game-state]}
   {:as player, :keys [:next-movement]}]
  {::gs/game-state game-state
   ::last-move next-movement
   ::previous-score score})

(s/fdef update-input-vector
  :args (-> (s/cat :old-input-vector ::input-vector
                   :params ::params
                   :data ::data)
            (s/and (fn [{ {{:keys [::gb/game-board]} ::gs/game-state} :data}]
                     (vision-depth-fits-game? game-board))))
  :ret ::input-vector)

(defn- update-input-vector
  [old-input-vector
   {:as params, :keys [::brain-tau]}
   {:as data, :keys [::previous-score ::last-move]
    {:keys [::gb/game-board ::gs/player-position ::gs/score]} ::gs/game-state}]
  (let [visible-matrix (visible-matrix game-board player-position)]
    (conj (visible-matrix-vector visible-matrix)
          (new-motoception (motoception old-input-vector) brain-tau last-move)
          (new-satiety (satiety old-input-vector) previous-score score brain-tau))))

(s/def ::senses (s/keys :req [::input-vector ::params ::data]))

(defn- initialize-senses
  [brain-tau game-state]
  {::input-vector (vec (repeat input-vector-size 0.0))
   ::params {::brain-tau brain-tau}
   ::data {::previous-score 0
           ::gs/game-state game-state
           ::last-move nil}})

(defn initialize-senses!
  [brain-tau game-state]
  (if (vision-depth-fits-game? (::gb/game-board game-state))
    (initialize-senses brain-tau game-state)
    (throw (java.lang.RuntimeException. "Vision depth incompatible w. game board"))))

(defn update-senses
  "Compute a new input-vector using its previous value and various game data,
  updating score with the previous score"
  [senses world player]
  (-> senses
      (update ::data update-data world player)
      (#(update % ::input-vector update-input-vector (::params %) (::data %)))))
