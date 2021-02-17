(ns mzero.ai.player
  "Player protocol, and player-related functions:
  - `request-movement` to request a move,
  - `get-player-senses` to get the subset of world
  data that non-trivial players may limit
  themselves to,
  - `load-player` to load a player."
  (:require [mzero.ai.world :as aiw]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.utils.modsubvec :refer [modsubvec]]
            [clojure.spec.gen.alpha :as gen]))

(defprotocol Player
  "A Player updates its state every time it needs via `update-player`.
  It can move by putting a non-nil direction for its move in its
  `:next-movement` field. `update-player` is called by
  `request-movement` and should probably not be called elsewhere.

  The 'world' will then execute the movement, which will reflect in
  the world state. It is possible that the player updates again
  *before* the world's execution of the movement, e.g. if the world
  has been paused by interactive mode. The player may have to take
  that into account, for instance by waiting until the movement is
  actually executed. When this happens, note that requesting the same
  movement rather than not requesting a movement might result in the
  movement being executed twice.

  Protocol implementations targeted at use by main.clj should be named
  {name}Player and reside in a namespace whose last fragment is
  {name}, e.g. the random player implementation will be `RandomPlayer`
  and will reside at `mzero.ai.players.random`, see `load-player`."
  
  (init-player [player opts world] "Implementation-specific
  initialization. Meant to be used by `load-player`, 
  and intended to be called with all fields of player
  record set to nil (any caller should expect fields to be ignored /
  overriden). Some opts may be implementation-specific and thus should
  be documented by implementations.

  The recommended way to create a player of any given
  impl is via `load-player`")

  (update-player [player world] "Updates player state, and ultimately
  the :next-movement field, every time a movement is requested."))

(def max-vision-depth
  "Vision depth defines half the size of a square centered on player,
  that the player can sense"
  (dec (int (/ gb/max-board-size 2))))

(s/def ::vision-depth (s/int-in 1 max-vision-depth))

(defn subset-size [vision-depth] (inc (* vision-depth 2)))

(s/def ::board-subset
  (-> (s/every (s/every ::gb/game-cell
                        :kind vector?
                        :min-count 3
                        :max-count (subset-size max-vision-depth))
               :kind vector?
               :min-count 3
               :max-count (subset-size max-vision-depth))
      (s/and (fn [gb]
               (comment "lines and rows have same size")
               (every? #(= (count %) (count gb)) gb)))
      (s/with-gen #(gen/bind (gen/choose 3 10)
                             gb/game-board-generator))))

(s/def ::player-senses
  (-> (s/keys :req [::board-subset ::aiw/game-step ::gs/score])))

(defn- get-board-sense-subset  
  [game-board player-position vision-depth]
  (let [size (subset-size vision-depth)
        offset-row (- (first player-position) vision-depth)
        offset-col (- (second player-position) vision-depth)]
    (->> (modsubvec game-board offset-row size)
         (map #(modsubvec % offset-col size))
         vec)))

(s/fdef get-player-senses
  :args (-> (s/cat :world ::aiw/world-state
                   :vision-depth ::vision-depth)
            (s/and (fn [{:keys [vision-depth] {{:keys [::gb/game-board]} ::gs/game-state} :world}]
                     (comment "Vision depth smaller than half the board")
                     (< vision-depth (/ (count game-board) 2))))
            (s/with-gen
              (fn []
                (gen/bind (gen/choose 5 100)
                          #(gen/tuple
                            (gen/fmap aiw/get-initial-world-state (gs/game-state-generator %))
                            (gen/choose 1 (dec (int (/ % 2)))))))))
  :ret ::player-senses)

(defn get-player-senses
  "The player can only sense the score, the number of steps, and cells
  of the board that are around it, up to `vision-depth` (square of odd
  edge 2*vision depth + 1, centered on player position)"
  [world vision-depth]
  {::aiw/game-step (-> world ::aiw/game-step)
   ::gs/score (-> world ::gs/game-state ::gs/score)
   ::board-subset (get-board-sense-subset (-> world ::gs/game-state ::gb/game-board)
                                          (-> world ::gs/game-state ::gs/player-position)
                                          vision-depth)})

(defn request-movement
  "Updates the player state, and indicates the player wishes "
  [player-state world-state]
  (swap! player-state update-player @world-state)
  (when (-> @player-state :next-movement)
    (swap! world-state
           assoc-in [::aiw/requested-movements :player]
           (-> @player-state :next-movement))))


(defn load-player
  [player-type opts world]
  (let [player-ns-string (str "mzero.ai.players." player-type)
        player-constructor-string
        (-> player-type
            ;; convert player-type to CamelCase
            (str/split #"-") (#(map str/capitalize %)) str/join
            ;; merge to constructor string
            (#(str player-ns-string "/map->" % "Player")))]
    
    (try
      ;; use of private fn serialized-require to avoid bugs due to
      ;; require not being thread safe ATTOW
      ;; see https://clojure.atlassian.net/browse/CLJ-1876
      (#'clojure.core/serialized-require (symbol player-ns-string))
      (init-player ((resolve (symbol player-constructor-string)) {}) opts world)
      (catch java.io.FileNotFoundException _
        (throw (RuntimeException.
                (format "Couldn't load player %s. Check player type matches a
                player implementation in `mzero.ai.players`" player-type)))))))
