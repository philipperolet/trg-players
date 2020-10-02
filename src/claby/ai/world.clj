(ns claby.ai.world
  "Module responsible for running the world, by:
  - listening to movement requests (on `requested-movements`);
  - updating the `world-state` according to the movement requests;
  - updating the world's timestamp every time its state changes
    (ATTOW only via requested-movements).

  Regarding the first element, movement requests can be made by the
  player as well as by enemies. The last element is intended to allow
  a detailed execution history.
  
  Two constraints are enforced :
  
  - **thread-safe consistency** between `game-state` and
  `requested-movements`, meaning if an external thread sees that
  `requested-movements` is empty, it means that game state has already
  been updated. Conversely, if `requested-movements` is not empty,
  `game-state` has *not* been updated with those movements;
  
  - **timeliness** of the game, meaning that executing requested
  movements should not take more than 1ms. The program will not halt
  at the first delay over 1ms, for stability. However, it will throw
  an exception if delays happen too much."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.utils :as u]
            [clojure.tools.logging :as log]))

;;; Full game state spec & helpers
;;;;;;;


;; Time interval (ms) between each game step
(def max-game-step-duration 1000)

(s/def ::game-step-duration (s/int-in 1 max-game-step-duration))

(s/def ::game-step nat-int?)

;; timestamp in ms of current step start
(s/def ::step-timestamp nat-int?)

;; time to wait = game step duration - step execution time (obtained
;; by comparing step timestamps before and after running the step) or
;; 0 if step execution time exceeded game step duration
(s/def ::time-to-wait (s/int-in 0 max-game-step-duration))

;; number of steps that were not performed fast enough (i.e. took more
;; than game-step-duration
(s/def ::missteps nat-int?)

(s/def ::requested-movements (s/map-of ::ge/being ::ge/direction))

(defn- world-state-predicate-matcher
  "Generator helper to match ::world-state constraints"
  [world-state]
  (-> world-state
      (update ::missteps min (world-state ::game-step))
      (assoc ::requested-movements
             (u/filter-keys
              #(or (= % :player)
                   (< % (count (get-in world-state
                                       [::gs/game-state ::gs/enemy-positions]))))
              (world-state ::requested-movements)))))

(def world-state-keys-spec
  (s/keys :req [::gs/game-state
                ::game-step
                ::requested-movements
                ::step-timestamp 
                ::time-to-wait
                ::missteps]))

(s/def ::world-state
  (-> world-state-keys-spec
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements)))
             (fn [{:keys [::missteps ::game-step]}]
               (comment "no more missteps than actual game steps + 1 (current)")
               (<= missteps (inc game-step))))
      (s/with-gen #(gen/fmap
                    world-state-predicate-matcher
                    (s/gen world-state-keys-spec)))))


(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step ::step-timestamp ::time-to-wait ::missteps]}]
  (str (format (str "Step %d\nTimestamp (mod 1 000 000) %d\nTime-to-wait %d\n"
                    "Missteps %d\n")
               game-step
               (mod step-timestamp 1000000)
               time-to-wait
               missteps)
       (gs/state->string game-state)))

(defn active?
  [world-state]
  (= :active (-> world-state ::gs/game-state ::gs/status)))

;;; Game initialization
;;;;;;

(defn get-initial-world-state
  ([game-state initial-timestamp]
   {::gs/game-state (assoc game-state ::gs/status :active)
    ::game-step 0
    ::requested-movements {}
    ::time-to-wait 0
    ::missteps 0
    ::step-timestamp initial-timestamp})
  ([game-state] (get-initial-world-state game-state (System/currentTimeMillis))))

(defn initialize-game
  "Sets everything up for the game to start (arg check, state reset, log)."
  [state-atom initial-state {:as opts, :keys [game-step-duration player-step-duration]}]
  {:pre [(s/valid? ::game-step-duration game-step-duration)
         (s/valid? ::game-step-duration player-step-duration)]}  
  (reset! state-atom (get-initial-world-state initial-state))
  (log/info "The game begins.\n" (data->string @state-atom)))

;;; Game execution
;;;;;;

(s/fdef compute-new-state
  :args (s/cat :world-state ::world-state)
  :ret ::world-state
  :fn (s/and
       (fn [{{:keys [world-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step world-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn compute-new-state
  "Computes the new state derived from running a step of the
  game. Executes movements until none is left or game is over."
  [{:as world-state, :keys [::requested-movements]}]
  (-> world-state
      (update ::gs/game-state
              (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
              requested-movements)
      (assoc ::requested-movements {})
      (update ::game-step inc)))

(defn run-step
  "Runs a step of the world."
  [world-state-atom logging-steps]
  (swap! world-state-atom assoc ::step-timestamp (System/currentTimeMillis))
  (swap! world-state-atom compute-new-state)

  ;; Log every logging-steps steps, or never if 0
  (when (and (pos? logging-steps)
             (zero? (mod (@world-state-atom ::game-step) logging-steps)))
    (log/info (data->string @world-state-atom))))
