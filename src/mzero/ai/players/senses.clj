(ns mzero.ai.players.senses
  "Module to compute player senses given world and player data at a
  given time, as a float-valued `::input-vector`containing valid
  `::neural-value`s (see network.clj).

  Senses are the part of the player interfacing between the player's
  brain and the rest of the world.

  Player senses are:
  -  its `vision`, which is a subset of the board cells
  for which it can see the cell contents (such cells are *visible* cells);
  - its `satiety`, which raises when it eats fruits and decreases when
  it doesn't;
  - its `motoception`, short for motricity perception, which activates
  when it moves and stays on for a while
  - its `aleaception`, random perception, 2 floats in [0,1[ with a new
  random value each time.

  Senses comprise:
  - `::input-vector` fed to the player brain;
  - inial `::params` necessary to compute them;
  - `::data` on the player and the world, updated at each iteration along
  with `::input-vector`.

  **Brain time constant, brain-tau**
  Represents the time taken to perceive things in 'brain iterations'
  -- it is for now the brain depth in # of layers, conditionning
  persistence for satiety & motoception. In humans, ~ 50ms to perceive
  things indicate a depth of about 50 for 1ms synapses.

  The module provide functions `initialize-senses!` and `udpate-senses`."
  (:require [mzero.game.board :as gb]
            [clojure.spec.alpha :as s]
            [mzero.utils.modsubvec :refer [modsubvec]]
            [mzero.game.state :as gs]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.network :as mzn]
            [mzero.game.events :as ge]
            [mzero.utils.utils :as u]
            [clojure.data.generators :as g]))

;; Brain time constant
(s/def ::brain-tau (s/int-in 1 200))

(defn- default-persistence [brain-tau] (* 2 brain-tau))

;; Vision
;;;;;;;;;

(def vision-depth
  "A player's `vision-depth` is the distance up to which it can see a
  cell content relatively to its current position.

  E.g. a player with vision depth 2 in position [3 0] can see cells [3
  2], [1 0] or [1 2] but not [3 3] or [0 0].

  Therefore, the player can *see* a square matrix of visible cells of
  edge length vision-depth*2 + 1."
  4)

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

(def last-vision-cell-index (vision-cell-index [vision-depth vision-depth]))
(defn vision-depth-fits-game?
  "`true` iff the vision matrix edge is smaller than the game board edge"
  [game-board]
  (<= visible-matrix-edge-size (count game-board)))

;; Motoception
;;;;;;;;;;;;;;

"Sens de taille 1, activé lorsque le joueur essaie de bouger

- donc si volonté de mouvement contre un mur, activé quand même (même
  si pas de mouvement effectif)
- Reste actif pendant *motoception-persistence* itérations si pas de
nouveaux movements, désactivé ensuite 
- similairement à la satiété, *motoception-persistence* dépend de
*brain-tau* 
- Si mouvement à nouveau pendant la persistence, on repart pour le
nombre total d'itérations (cas limite, devrait peu arriver)"

(def min-motoception-activation 0.95)
(def motoception-activation-value 1.0)

(s/def ::last-move (s/or :nil nil?
                        :direction ::ge/direction))

(s/def ::motoception (-> ::mzn/neural-value
                         (s/and #(or (= % 0.0) (>= % min-motoception-activation)))
                         (s/with-gen
                           #(gen/one-of
                             [(s/gen (s/double-in min-motoception-activation motoception-activation-value))
                              (gen/return 0.0)]))))

(s/fdef new-motoception
  :args (s/cat :old-motoception ::motoception
               :brain-tau ::brain-tau
               :last-move ::last-move)
  :ret ::motoception)

(defn motoception-persistence [brain-tau] brain-tau)

(defn- new-motoception
  "Compute the motoception value.

  To know if motoception should deactivate, i.e. if
  *motoception-persistence* iterations, computed from `brain-tau`,
  occured with no new move from player, without storing previous
  state, the motoception value is decreased from a small increment
  every time. An added benefit is the ability for the player to make
  use of the differences in activation values (v.s. keeping the
  activation value fixed during *motoception-persistence*
  iterations)."
  [old-motoception brain-tau last-move]
  (let [increment
        (-> (- motoception-activation-value min-motoception-activation)
            (/ (motoception-persistence brain-tau)))
        
        new-motoception
        (- old-motoception increment)]
    (cond
      (some? last-move) motoception-activation-value
      (> new-motoception min-motoception-activation) new-motoception
      :else 0.0)))

(def motoception-index (inc last-vision-cell-index))
(defn motoception [input-vector] (nth input-vector motoception-index))

;; Satiety
;;;;;;;;;;

"Sens de taille 1. Se calcule comme suit (dans `new-satiety`):

- à chaque fruit mangé on ajoute à (< 1) à sa valeur précédente, le *fruit-increment*
- décroit chaque tour, valeur multipliée par b (< 1)
- cappé à 1 max, c (< 1) min. c est la *minimal-satiety*

Le sentiment de fruit mangé reste ainsi K tours (eq : a * b^k < c)

Valeurs: on part sur a = 0.3, k ~ 40, c ~ 0.04 => b=0.95

On ajuste k en fonction de la constante de temps du cerveau,
*brain-tau*, liée à la profondeur du réseau.  La logique sous-jacente
c'est que la persistence rétinienne doit correspondre à la durée de
prise de conscience du réseau, càd sa profondeur

Raisonnement : possibilité de manger 4 fruits de suite (après plus
d'impact), besoin de garder le sens qu'on a mangé un fruit disponible
pendant quelques tours."

(def minimal-satiety 0.04)
(def satiety-index (inc motoception-index))
(s/def ::previous-score ::gs/score)

(s/def ::satiety (-> ::mzn/neural-value
                     (s/and #(or (>= % minimal-satiety) (= % 0.0)))
                     (s/with-gen (fn [] (gen/fmap
                                         #(if (< % minimal-satiety) 0.0 %)
                                         (s/gen ::mzn/neural-value))))))

(defn- satiety [input-vector] (nth input-vector satiety-index))

(s/fdef new-satiety
  :args (-> (s/cat :old-satiety ::satiety
                   :previous-score ::gs/score
                   :score ::previous-score
                   :brain-tau ::brain-tau))
  :ret ::satiety)

(defn- new-satiety
  "Compute satiety from its former value `old-satiety` and whether the
  score increased at this step."
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

;; Aleaception
;;;;;;;;;;;;;;
"Sens de taille 2, perception aléatoire."

(def aleaception-index (inc satiety-index))
(defn- new-aleaception
  "Compute aleaception given a RNG : 2 floats between 0 and 1"
  [rng]
  (binding [g/*rnd* rng]
    [(g/float) (g/float)]))

;; Input vector
;;;;;;;;;;;;;;;;

(def input-vector-size
  "Size of senses vector = number of visible cells + 1 (satiety) +
  1 (motoception) + 2 (aleaception)"
  (int (+ 4 (Math/pow visible-matrix-edge-size 2))))

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
(s/def ::rng #(instance? java.util.Random %))
(s/def ::params (s/keys :req [::brain-tau ::rng]))
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
   {:as params, :keys [::brain-tau ::rng]}
   {:as data, :keys [::previous-score ::last-move]
    {:keys [::gb/game-board ::gs/player-position ::gs/score]} ::gs/game-state}]
  (let [visible-matrix (visible-matrix game-board player-position)
        [alea1 alea2] (new-aleaception rng)]
    (conj (visible-matrix-vector visible-matrix)
          (new-motoception (motoception old-input-vector) brain-tau last-move)
          (new-satiety (satiety old-input-vector) previous-score score brain-tau)
          alea1 alea2)))

(s/def ::senses (s/keys :req [::input-vector ::params ::data]))

(defn- initialize-senses
  [brain-tau game-state rng]
  {::input-vector (vec (repeat input-vector-size 0.0))
   ::params {::brain-tau brain-tau ::rng rng}
   ::data {::previous-score 0
           ::gs/game-state game-state
           ::last-move nil}})

(defn initialize-senses!
  [brain-tau game-state rng]
  (if (vision-depth-fits-game? (::gb/game-board game-state))
    (initialize-senses brain-tau game-state rng)
    (throw (java.lang.RuntimeException. "Vision depth incompatible w. game board"))))

(defn update-senses
  "Compute a new input-vector using its previous value and various game data,
  updating score with the previous score"
  [senses world player]
  (-> senses
      (update ::data update-data world player)
      (#(update % ::input-vector update-input-vector (::params %) (::data %)))))
