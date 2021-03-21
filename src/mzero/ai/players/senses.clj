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

  The module provide functions `initialize-senses` and `udpate-senses`.

  See arch minor for more details."
  (:require [mzero.game.board :as gb]
            [clojure.spec.alpha :as s]
            [mzero.utils.modsubvec :refer [modsubvec]]
            [mzero.game.state :as gs]
            [clojure.spec.gen.alpha :as gen]
            [mzero.ai.players.activation :as mza]
            [mzero.game.events :as ge]))

;; Brain time constant
(s/def ::brain-tau (s/int-in 1 200))

(defn- default-persistence [brain-tau] (* 2 brain-tau))

;; Satiety
;;;;;;;;;;

(def minimal-satiety 0.04)

(s/def ::previous-score ::gs/score)

(s/def ::satiety (-> ::mza/neural-value
                     (s/and #(or (>= % minimal-satiety) (= % 0.0)))
                     (s/with-gen (fn [] (gen/fmap
                                         #(if (< % minimal-satiety) 0.0 %)
                                         (s/gen ::mza/neural-value))))))

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

(s/def ::motoception (-> ::mza/neural-value
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


;; Input vector
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
(s/def ::params (s/keys :req [::vision-depth ::brain-tau]))
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
            (s/and (fn [{{:keys [::vision-depth]} :params
                         {{:keys [::gb/game-board]} ::gs/game-state} :data}]
                     (vision-depth-fits-game? vision-depth game-board))))
  :ret ::input-vector)

(defn- update-input-vector
  [old-input-vector
   {:as params, :keys [::vision-depth ::brain-tau]}
   {:as data, :keys [::previous-score ::last-move]
    {:keys [::gb/game-board ::gs/player-position ::gs/score]} ::gs/game-state}]
  (let [visible-matrix (visible-matrix game-board player-position vision-depth)]
    (conj (visible-matrix-vector visible-matrix)
          (new-motoception (motoception old-input-vector)
                           brain-tau
                           last-move)
          (new-satiety (satiety old-input-vector) previous-score score brain-tau))))

(defn- senses-generator [vision-depth]
  (gen/hash-map ::params (gen/hash-map ::vision-depth (gen/return vision-depth)
                                      ::brain-tau (s/gen ::brain-tau))
                ::data (s/gen ::data)
                ::input-vector (s/gen (input-vector-spec vision-depth))))

(s/def ::senses
  (-> (s/keys :req [::input-vector ::params ::data])
      (s/and (fn [{:keys [::input-vector], {:keys [::vision-depth]} ::params}]
               (comment "Senses vector size depends on vision depth")
               (= (count input-vector) (input-vector-size vision-depth))))
      (s/with-gen #(gen/bind (s/gen ::vision-depth) senses-generator))))

(defn initialize-senses
  [vision-depth brain-tau game-state]
  {::input-vector (vec (repeat (input-vector-size vision-depth) 0.0))
   ::params {::vision-depth vision-depth
             ::brain-tau brain-tau}
   ::data {::previous-score 0
           ::gs/game-state game-state
           ::last-move nil}})

(defn update-senses
  "Compute a new input-vector using its previous value and various game data,
  updating score with the previous score"
  [senses world player]
  (-> senses
      (update ::data update-data world player)
      (#(update % ::input-vector update-input-vector (::params %) (::data %)))))
