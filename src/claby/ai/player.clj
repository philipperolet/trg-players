(ns claby.ai.player
  "Player protocol."
  (:require [claby.ai.world :as aiw]
            [clojure.string :as str]))

(defprotocol Player
  "A Player updates its state every time it needs via `update-player`.
  It can move by putting a non-nil direction for its move in its
  `:next-movement` field.

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
  and will reside at `claby.ai.players.random`, see
  `load-player-constructor-by-name`."
  
  (init-player [player world] "Returns a fully initialized
  player. Should be called before the first call to
  update-player. Intended to be callled with all fields of player
  record set to nil")
  (update-player [player world] "Updates player state, and ultimately
  the :next-movement field, every time a movement is requested."))

(defn request-movement
  [player-state world-state]
  (swap! player-state update-player @world-state)
  (when (-> @player-state :next-movement)
    (swap! world-state
           assoc-in [::aiw/requested-movements :player]
           (-> @player-state :next-movement))))

(defn load-player-constructor-by-name
  [player-name]
  (let [player-ns-string
        (str "claby.ai.players." player-name)
        player-constructor-string
        (str player-ns-string "/map->" (str/capitalize player-name) "Player")]
    (require (symbol player-ns-string))
    (resolve (symbol player-constructor-string))))
