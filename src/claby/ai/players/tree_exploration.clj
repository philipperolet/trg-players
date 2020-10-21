(ns claby.ai.players.tree-exploration
  "Tree exploration player. Monte-carlo simulations are performed using a data
  structure called an `tree-node`, which has a `value`, a `frequency`,
  a `direction` and  a `children` map, mapping directions to tree-nodes.

  Frequency stores the number of times the node has been
  visited. Value stores the smallest number of steps to a fruit found
  so far from this node."
  (:require [claby.ai.player :as aip]
            [claby.game.events :as ge]
            [claby.game.state :as gs]
            [claby.game.board :as gb]
            [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [claby.utils :as u]))

(s/def ::node-value nat-int?)

(s/def ::node-frequency nat-int?)

(s/def ::children nil)

(s/def ::tree-node (s/keys :req [::frequency ::children]
                           :opt [::value ::ge/direction]))

(s/def ::children (s/map-of ::ge/direction ::tree-node :max-count 4 :distinct true))

(def default-nb-sims 200)

(defn- max-sim-size [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (* board-size board-size)))

(defn- worst-value
  "Value given to a node once the end of the simulation is reached and
  no fruit has been found."
  [game-state]
  (* 2 (max-sim-size game-state)))

(defn- min-child
  "Returns the child of tree-node with the minimum value."
  [tree-node]
  (apply min-key ::value (vals (::children tree-node))))

(defn- select-next
  [node-children]
  (if (< (count node-children) 4)
    ;; new node with direction not already in `node-children`
    {::ge/direction (-> ge/directions
                        (cset/difference (set (keys node-children)))
                        first)
     ::frequency 0
     ::children {}}
    ;; less visited child is selected
    (apply min-key ::frequency (vals node-children))))

(s/fdef tree-simulate
  :args (s/cat :game-state ::gs/game-state
               :sim-size nat-int?
               :tree-node ::tree-node)
  :ret ::tree-node)

(defn- tree-simulate
  "Simulates `sim-size` steps of a game from `game-state`, with
  exploration data at current step stored in `tree-node`.
  Returns tree node with updated data."
  [game-state sim-size tree-node]
  (let [next-state
        (if-let [direction (::ge/direction tree-node)]
          (ge/move-player game-state direction)
          game-state)]
    (cond
      (< (::gs/score game-state) (::gs/score next-state))
      (assoc tree-node ::value 0)

      (or (zero? sim-size) (not (= (::gs/status next-state) :active)))
      (assoc tree-node ::value (worst-value game-state))

      :else
      (let [next-node (select-next (-> tree-node ::children))]
        (-> tree-node
            (assoc-in [::children (::ge/direction next-node)]
                      (tree-simulate next-state (dec sim-size) next-node))
            (update ::frequency inc)
            (#(assoc % ::value (inc (::value (min-child %))))))))))

(defn- simulate-games
  [player game-state]
  (let [simulate-once-from-node
        (partial tree-simulate game-state (max-sim-size game-state))]
    
    (->> (-> player :root-node)
         (iterate simulate-once-from-node)
         (#(nth % (-> player :nb-sims)))
         (assoc player :root-node))))

(s/def ::options (s/map-of #{:nb-sims} pos-int?))

(defn node-path
  "Return `tree-node` with its children only (not its children's
  children, etc). If `directions` are given, then shows the node, the
  child from the 1st provided direction, the child of this child
  provided by the second direction, etc."
  [tree-node & directions]
  (if (empty? directions)
    ;; remove "children" fields from the node's children
    (update tree-node ::children (partial u/map-map #(dissoc % ::children)))
    
    (-> tree-node
        (update ::children #(select-keys % (list (first directions))))
        (update-in [::children (first directions)]
                   #(apply node-path % (rest directions))))))

(defrecord TreeExplorationPlayer [root-node nb-sims]
  aip/Player
  (init-player [this opts world]
    (assert (s/valid? ::options opts))
    (assoc this
           :root-node {::frequency 0 ::children {}}
           :nb-sims (-> opts (:nb-sims default-nb-sims))))
  (update-player [this world]
    (let [new-root
          (if (empty? (::children root-node))
            root-node
            (min-child root-node))]
      
      (-> this
          (assoc :root-node new-root)
          (simulate-games (-> world ::gs/game-state))
          (#(assoc % :next-movement (->  % :root-node min-child ::ge/direction)))))))
