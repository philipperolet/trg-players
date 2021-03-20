(ns mzero.ai.players.senses
  "Module to compute player senses given world data at a given time, as
  a float-valued `::input-vector`containing valid neural values (see
  activation.clj).

  Computation of the senses requires the previous senses value,
  as well as a `vision-depth` and the previous `score`, both of which
  are kept in `::senses-data`.

  The module's main function is `udpate-senses-data`, performing the
  senses computation. `initial-senses-data` is also part of the public
  interface.

  Player senses are:
  -  its `vision`, which is a subset of the board cells
  for which it can see the cell contents (such cells are *visible* cells);
  - its `satiety`, which raises when it eats fruits and decreases when
  it doesn't;
  - its `motoception`, short for motricity perception, which activates
  when it moves and stays on for a while

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
            [mzero.utils.modsubvec :refer [modsubvec]]
            [mzero.game.state :as gs]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.activation :as mza]
            [mzero.game.events :as ge]))

;; Satiety
;;;;;;;;;;

(def minimal-satiety 0.04)

(s/def ::satiety (-> ::mza/neural-value
                     (s/and #(or (>= % minimal-satiety) (= % 0.0)))
                     (s/with-gen (fn [] (gen/fmap
                                         #(if (< % minimal-satiety) 0.0 %)
                                         (s/gen ::mza/neural-value))))))

(defn- satiety [senses] (last senses))

(s/fdef new-satiety
  :args (-> (s/cat :old-satiety ::satiety
                   :previous-score ::gs/score
                   :score ::gs/score
                   :satiety-persistence (s/int-in 1 100)))
  :ret ::satiety)

(defn- new-satiety
  "Compute satiety from its former value `old-satiety` and whether the
  score increased at this step. see m0.0.1 notes for explanations."
  [old-satiety previous-score score satiety-persistence]
  (let [fruit-increment 0.3
        decrease-factor (Math/pow (/ minimal-satiety fruit-increment) (/ satiety-persistence))
        fruit-eaten-increment (if (< previous-score score) fruit-increment 0.0)
        new-satiety (+ (* old-satiety decrease-factor) fruit-eaten-increment)]
    (cond
      (< new-satiety minimal-satiety) 0.0
      (> new-satiety 1) 1.0
      :else new-satiety)))

;; Motoception
;;;;;;;;;;;;;;
(def max-motoception-persitence 1000)
(def min-motoception-activation 0.95)
(def activation-value 1.0)

(s/def ::motoception-persistence (s/int-in 1 max-motoception-persitence))

(s/def ::motoception (-> ::mza/neural-value
                         (s/and #(or (= % 0.0) (>= % min-motoception-activation)))
                         (s/with-gen
                           #(gen/one-of
                             [(s/gen (s/double-in min-motoception-activation activation-value))
                              (gen/return 0.0)]))))

(s/fdef new-motoception
  :args (s/cat :old-motoception ::motoception
               :motoception-persistence ::motoception-persistence
               :last-move (s/or :nil nil? :direction ::ge/direction))
  :ret ::motoception)

(defn- new-motoception
  "Compute the motoception value.

- A chaque fois qu'il y a une volonté de mouvement, activé 
  
- donc si volonté de mouvement contre un mur, activé quand même (même
si pas de mouvement effectif)

- Reste actif pendant *motoception-persistence* itérations si pas de
nouveaux movements, désactivé ensuite. Similairement à la satiété,
*motoception-persistence* dépend de *network-depth*

- Si mouvement à nouveau pendant la persistence, on repart pour le
  nombre total d'itérations (cas limite, devrait peu arriver)

**Implémentation**

To know if motoception should deactivate, i.e. if
`motoception-persistence` iterations occured with no new move from
player, without storing previous state, the motoception value is
decreased from a small increment every time. An added benefit is the
ability for the player to make use of the differences in activation
values (v.s. keeping the activation value fixed during
*motoception-persistence* iterations)."
  [old-motoception motoception-persistence last-move]
  (let [increment
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

(def min-vision-depth 1)
(def max-vision-depth (dec (int (/ gb/max-board-size 2))))

(s/def ::vision-depth (s/int-in 1 max-vision-depth))

(defn visible-matrix-edge-size
  "Edge size of the matrix of visible cells"
  [vision-depth]
  (inc (* vision-depth 2)))

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
  [visible-keyword-matrix]
  (->> visible-keyword-matrix
       (reduce into [])
       (map {:wall 1.0 :empty 0.0 :fruit 0.5 :cheese 0.2})
       vec))

(defn vision-depth-fits-game?
  "`true` iff the vision matrix edge is smaller than the game board edge"
  [vision-depth game-board]
  (<= (visible-matrix-edge-size vision-depth) (count game-board)))


;; Senses vector
;;;;;;;;;;;;;;;;

(defn input-vector-size
  "Size of senses vector = number of visible cells + 1 (satiety) +
  1 (motoception)"
  [vision-depth]
  (int (+ 2 (Math/pow (visible-matrix-edge-size vision-depth) 2))))


(defn- input-vector-spec
  "Return a spec of senses vector fitting vision-depth"
  [vision-depth]
  (let [min-count (input-vector-size (or vision-depth min-vision-depth))
        max-count (input-vector-size (or vision-depth max-vision-depth))
        spec-def (s/every ::mza/neural-value
                          :kind vector?
                          :min-count min-count
                          :max-count max-count)
        generator-function
        (fn []
          (gen/fmap #(assoc %
                            (- min-count 2) (gen/generate (s/gen ::motoception))
                            (dec min-count) (gen/generate (s/gen ::satiety)))
                    (s/gen spec-def)))]
    (-> spec-def
        (s/and (fn [sv]
                 (comment "Vector must have a valid satiety")
                 (s/valid? ::satiety (satiety sv)))
               (fn [sv]
                 (comment "Vector must have a valid motoception")
                 (s/valid? ::motoception (motoception sv))))
        (s/with-gen generator-function))))

(s/def ::input-vector (input-vector-spec nil))
(s/def ::previous-score ::gs/score)

(defn- senses-data-generator [vision-depth]
  (gen/hash-map ::previous-score (s/gen ::previous-score)
                ::vision-depth (gen/return vision-depth)
                ::motoception-persistence (s/gen ::motoception-persistence)
                ::input-vector (s/gen (input-vector-spec vision-depth))))

(s/def ::senses-data
  (-> (s/keys :req [::input-vector ::previous-score
                    ::vision-depth ::motoception-persistence])
      (s/and (fn [{:keys [::vision-depth ::input-vector]}]
               (comment "Senses vector size depends on vision depth")
               (= (count input-vector) (input-vector-size vision-depth))))
      (s/with-gen #(gen/bind (s/gen ::vision-depth) senses-data-generator))))

(defn initial-senses-data
  [vision-depth motoception-persistence]
  {::input-vector (vec (repeat (input-vector-size vision-depth) 0.0))
   ::vision-depth vision-depth
   ::motoception-persistence motoception-persistence
   ::previous-score 0})

(defn- update-input-vector
  [old-input-vector senses-data game-state last-move satiety-persistence]
  (let [{:keys [::previous-score ::vision-depth ::motoception-persistence]} senses-data
        {:keys [::gb/game-board ::gs/player-position ::gs/score]} game-state
        visible-matrix (visible-matrix game-board player-position vision-depth)]
    
    (conj (visible-matrix-vector visible-matrix)
          (new-motoception (motoception old-input-vector)
                           motoception-persistence
                           last-move)
          (new-satiety (satiety old-input-vector) previous-score score satiety-persistence))))

(s/fdef update-senses-data
  :args (-> (s/cat :senses-data ::senses-data
                   :game-state ::gs/game-state
                   :last-move (s/or :nil nil?
                                    :direction ::ge/direction))
            (s/and (fn [{{:keys [::vision-depth]} :senses-data
                         {:keys [::gb/game-board]} :game-state}]
                     (vision-depth-fits-game? vision-depth game-board))))
  :ret ::senses-data)

(def default-satiety-persistence 40)

(defn update-senses-data
  "Compute a new input-vector using its previous value and various game data,
  updating score with the previous score"
  [senses-data game-state last-move]
  (-> senses-data
      (update ::input-vector update-input-vector senses-data game-state last-move default-satiety-persistence)
      (assoc ::previous-score (game-state ::gs/score))))
