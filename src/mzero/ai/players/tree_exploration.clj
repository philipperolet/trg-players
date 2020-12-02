(ns mzero.ai.players.tree-exploration
  "Tree exploration player. Game simulations are performed using a data
  structure called a `tree-node`, which has a `value`, a `frequency`,
  a `direction` and  a `children` map, mapping directions to tree-nodes.

  Frequency stores the number of times the node has been
  visited. Value stores the smallest number of steps to a fruit found
  so far from this node.

  There can be multiple implementations of a tree node, using the
  TreeExplorationNode protocol."
  (:require [mzero.ai.player :as aip]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [mzero.utils.utils :as u]
            [clojure.data.generators :as g]))

(def default-nb-sims 200)

(defprotocol TreeExplorationNode
  "Interface for tree nodes--methods to perform tree exploration.

  An implementation should supply a constructor (fn [game-state]
  ...). It can then replace the default implementation of
  tree-exploration-player by providing the constructor name in the
  player options: {:node-constructor constr-name}"
  (append-child [this direction]
    "Adds a new child to the given direction provided it doesn't
    already exist. Throw if it does.")
  (-tree-simulate [this game-state sim-size]
    "Simulate `sim-size` steps of a game from `game-state`, with
  exploration data at current step stored in `tree-node`.
  Returns tree node with updated data."))

(s/def ::value nat-int?)

(s/def ::frequency nat-int?)

(s/def ::tree-node (s/keys :req [::frequency ::children] :opt [::value]))

(s/def ::children (s/map-of ::ge/direction ::tree-node :max-count 4 :distinct true))

(defn- max-sim-size [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (* 2 board-size)))

(defn- worst-value
  "Value given to a node once the end of the simulation is reached and
  no fruit has been found."
  [game-state]
  (* 2 (max-sim-size game-state)))

(defn- min-direction
  "Returns the direction of `tree-node` whose corresponding child node
  has `sort-key` at its min."
  ([tree-node sort-key]
   (apply min-key
          #(-> tree-node ::children % sort-key)
          ;; directions for which there are children
          (keys (-> tree-node ::children)))))

(defn- update-children
  "Adds a child if not all have been generated yet."
  [node]
  (if-let [missing-child-direction
           (first (cset/difference ge/directions (set (keys (::children node)))))]
    (append-child node missing-child-direction)
    node))

(s/fdef tree-simulate
  :args (s/and (s/cat :tree-node ::tree-node                      
                      :game-state ::gs/game-state
                      :sim-size nat-int?))
  :ret ::tree-node)

(defn tree-simulate
  [tree-node game-state sim-size]
  (-tree-simulate tree-node game-state sim-size))

(defn- simulate-games
  [game-state node nb-sims]
  (let [simulate-once-from-node
        #(tree-simulate % game-state (max-sim-size game-state))]    
    (->> node
         (iterate simulate-once-from-node)
         (#(nth % nb-sims)))))

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

(defn- simulate-on-each-direction
  "Run simulations on a given direction, for the purpose of parallel
  computing with a factor 4."
  [this world direction]
  (let [state-at-direction (ge/move-player (::gs/game-state world) direction)]
    (if (< (::gs/score (::gs/game-state world)) (::gs/score state-at-direction))
      ;; if the movement to this direction in finding a fruit, it's a win
      {direction (-> ((:node-constructor this) state-at-direction)
                     (assoc ::value 0))}
      ;; else, do the whole simulation
      {direction  (simulate-games state-at-direction
                                  ((:node-constructor this) state-at-direction)
                                  (/ (-> this :nb-sims) (count ge/directions)))})))

(defn- compute-root-node [this world]
  (->> ge/directions
       (pmap (partial simulate-on-each-direction this world))
       (apply merge)
       (assoc ((:node-constructor this) (::gs/game-state world)) ::children)))

(defrecord TreeExplorationNodeImpl []
  TreeExplorationNode

  (append-child [this direction]
    (assoc-in this [::children direction]
              (map->TreeExplorationNodeImpl {::children {} ::frequency 0})))

  (-tree-simulate [tree-node game-state sim-size]
    (-> (cond
          ;; score to MAX_VALUE used as flag to indicate a fruit was eaten
          (= (::gs/score game-state) Integer/MAX_VALUE)
          (assoc tree-node ::value 0)

          (or (zero? sim-size) (= (::gs/status game-state) :over))
          (assoc tree-node ::value (worst-value game-state))

          :else
          (let [updated-node (update-children tree-node)
                next-direction (min-direction updated-node ::frequency)
                min-child-value-plus1
                #(inc (::value ((min-direction % ::value) (::children %))))
                next-state
                (let [ns (ge/move-player game-state next-direction)]
                  (cond-> ns
                    (< (::gs/score game-state) (::gs/score ns))
                    (assoc ::gs/score Integer/MAX_VALUE)))]
            (-> (update-in updated-node
                           [::children next-direction]
                           #(tree-simulate % next-state (dec sim-size)))
                (#(assoc % ::value (min-child-value-plus1 %))))))
        (update ::frequency inc))))

(defn te-node
  "TreeExplorationNodeImpl constructor"
  [game-state]
  (assoc (->TreeExplorationNodeImpl)
         ::children {}
         ::frequency 0
         ::value (worst-value game-state)))

(s/def ::options (s/map-of #{:nb-sims :node-constructor :seed} any?))

(defrecord TreeExplorationPlayer [nb-sims]
  aip/Player
  (init-player [this opts world]
    (let [constructor
          (->> (-> opts (:node-constructor "te-node"))
               (str "mzero.ai.players.tree-exploration/")
               symbol
               resolve)
          random-number-generator
          (if-let [seed (-> opts :seed)]
            (java.util.Random. seed)
            (java.util.Random.))]
      
      (assert (s/valid? ::options opts))
      (assert (not (nil? constructor)) "Invalid user-supplied node constructor")
      (assoc this
             :nb-sims (-> opts (:nb-sims default-nb-sims))
             :node-constructor constructor
             :rng random-number-generator)))
  
  (update-player [this world]
    (let [random-min-direction
          (fn [node]
            (binding [g/*rnd* (:rng this)]
              (let [min-value (apply min (map ::value (vals (::children node))))]
                (->> ge/directions
                     (filter #(= (-> node ::children % ::value) min-value))
                     g/rand-nth))))]
      
      (-> this
          (assoc :root-node (compute-root-node this world))
          (#(assoc % :next-movement (->  % :root-node random-min-direction)))))))
