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
  (-value [this] "Get node value")
  (-assoc-value [this val_])
  (-frequency [this] "Get node freq")
  (-assoc-frequency [this freq])
  (-children [this] "Get node children")
  (update-children-in-direction [this direction f])
  (get-child [this direction]
    "Get the child of this node in  `direction`")
  (min-direction [this sort-key]
    "Returns the direction of `this` tree-node whose corresponding child node
  has `sort-key` at its min.")
  (-create-root-node [this children]
    "Creates a root node form children direction such that the
    random-min-direction of update-player works correctly, i.e. each
    child obtained via a call to `children` for the root node will
    have a defined value. Used for the purpose of 4-fold //ization
    with each direction, as done in `compute-root-node`"))

(s/def ::value (s/or :natural nat-int? :infinite #(= % ##Inf)))

(s/def ::frequency nat-int?)
#_(s/keys :req [::frequency ::children] :opt [::value])
(s/def ::tree-node (partial satisfies? TreeExplorationNode))

(s/def ::children (s/map-of ::ge/direction ::tree-node :max-count 4 :distinct true))

(s/fdef value :args (s/cat :this ::tree-node) :ret ::value)
(defn value [this] (-value this))

(s/fdef frequency :args (s/cat :this ::tree-node) :ret ::frequency)
(defn frequency [this] (-frequency this))

(s/fdef assoc-value
  :args (s/cat :this ::tree-node :val_ ::value)
  :ret ::tree-node)
(defn assoc-value [this val_] (-assoc-value this val_))

(s/fdef assoc-frequency
  :args (s/cat :this ::tree-node :freq ::frequency)
  :ret ::tree-node)
(defn assoc-frequency [this freq] (-assoc-frequency this freq))

(s/fdef children
  :args (s/cat :this ::tree-node)
  :ret ::children)
(defn children [this] (-children this))

(s/fdef create-root-node
  :args (s/cat :this ::tree-node
               :children ::children)
  :ret ::tree-node)
(defn create-root-node [this children] (-create-root-node this children))

(defn get-descendant [node path]
  (if (empty? path)
      node
      (recur (get-child node (first path)) (rest path))))

(defn- max-sim-size [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (* 2 board-size)))

(defn- update-children
  "Adds a child if not all have been generated yet."
  [node]
  (if-let [missing-child-direction
           (first (cset/difference ge/directions (set (keys (children node)))))]
    (append-child node missing-child-direction)
    node))

(s/fdef tree-simulate
  :args (s/and (s/cat :tree-node ::tree-node                      
                      :game-state ::gs/game-state
                      :sim-size nat-int?))
  :ret ::tree-node)

(defn tree-simulate
  "Simulate `sim-size` steps of a game from `game-state`, with
  exploration data at current step stored in `tree-node`.
  Returns tree node with updated data."
  [tree-node game-state sim-size]
  (-> (cond
        ;; score to MAX_VALUE used as flag to indicate a fruit was eaten
        (= (::gs/score game-state) Integer/MAX_VALUE)
        (assoc-value tree-node 0)
        ;; status :over used as flag to indicate a wall is here
        (or (zero? sim-size) (= (::gs/status game-state) :over))
        tree-node

        :else
        (let [updated-node (update-children tree-node)
              next-direction (min-direction updated-node frequency)
              min-child-value-plus1
              #(inc (value ((min-direction % value) (children %))))
              next-state
              (let [ns (ge/move-player game-state next-direction)]
                (cond-> ns
                  (< (::gs/score game-state) (::gs/score ns))
                  (assoc ::gs/score Integer/MAX_VALUE)

                  (= (::gs/player-position game-state) (::gs/player-position ns))
                  (assoc ::gs/status :over)))]
          (-> (update-children-in-direction updated-node
                                            next-direction
                                            #(tree-simulate % next-state (dec sim-size)))
              (#(assoc-value % (min-child-value-plus1 %))))))
      (#(assoc-frequency % (inc (frequency %))))))

(defn- simulate-games
  [game-state node nb-sims]
  (let [simulate-once-from-node
        #(tree-simulate % game-state (max-sim-size game-state))]    
    (->> node
         (iterate simulate-once-from-node)
         (#(nth % nb-sims)))))

(defn node-path
  "For TENImpl, not DNImpl. Return `tree-node` with its children
  only (not its children's children, etc). If `directions` are given,
  then shows the node, the child from the 1st provided direction, the
  child of this child provided by the second direction, etc."
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
                     (assoc-value 0))}
      ;; else, do the whole simulation
      {direction  (simulate-games state-at-direction
                                  ((:node-constructor this) state-at-direction)
                                  (/ (-> this :nb-sims) (count ge/directions)))})))

(defn- compute-root-node [this world]
  (->> ge/directions
       (pmap (partial simulate-on-each-direction this world))
       (apply merge)
       (create-root-node ((:node-constructor this) (::gs/game-state world)))))

(defrecord TreeExplorationNodeImpl []
  TreeExplorationNode

  (append-child [this direction]
    (assoc-in this [::children direction]
              (map->TreeExplorationNodeImpl
               {::children {} ::value ##Inf ::frequency 0})))
  (-value [this] (::value this))
  (-assoc-value [this val_] (assoc this ::value val_))
  (-frequency [this] (::frequency this))
  (-assoc-frequency [this freq] (assoc this ::frequency freq))
  (-children [this] (::children this))
  (min-direction [this sort-key]
    (apply min-key
           #(-> this children % sort-key)
           ;; directions for which there are children
           (keys (-> this children))))
  (update-children-in-direction [this direction f]
    (update-in this [::children direction] f))
  (get-child [this direction]
    (get-in this [::children direction]))
  (-create-root-node [this children]
    (assoc this ::children children)))

(defn te-node
  "TreeExplorationNodeImpl constructor"
  [_]
  (assoc (->TreeExplorationNodeImpl)
         ::children {}
         ::frequency 0
         ::value ##Inf))

(defn- merge-nodes
  "Given 2 nodes with freq/value data, return a new node with merged data."
  [node node2]
  (if (and node node2)
    (-> node
        (update ::frequency #(+ % (-> node2 ::frequency)))
        (update ::value #(min % (-> node2 ::value))))
    (if node node node2)))

(defn- update-this-dag-node
  "For all elts of the child's dag map, update the root node dag-map's
  corresponding elt"
  [this [direction child]]
  (let [relative-position
        #(ge/move-position % direction (-> this :board-size))
        update-relative-position
        (fn [acc pos node]
          (update-in acc [:dag-map (relative-position pos)] #(merge-nodes % node)))]
    (reduce-kv update-relative-position this (-> child :dag-map))))

(def get-children-position-map
  (memoize
   (fn [position board-size]
     (reduce #(assoc %1 %2 (ge/move-position position %2 board-size)) {} ge/directions))))

"DagNodeImpl: nodes are shallow, no data except for position information on the
board (incl. dag map & board size). Data is held by the `dag-map`"
(defrecord DagNodeImpl [dag-map position board-size]
  TreeExplorationNode

  (append-child [this direction]
    (let [child-position
          (ge/move-position (-> this :position) direction (-> this :board-size))
          init-child-if-nil
          (fn [nd]
            (if nd nd (map->TreeExplorationNodeImpl
                       {::frequency 0
                        ::value (inc (-> this value))})))]
      (update this :dag-map #(update % child-position init-child-if-nil))))
  
  (-value [this] (get-in this [:dag-map (-> this :position) ::value]))
  (-assoc-value [this val_]
    (update this :dag-map
            #(assoc-in % [(-> this :position) ::value] val_)))
  (-frequency [this] (get-in this [:dag-map (-> this :position) ::frequency]))
  (-assoc-frequency [this freq]
    (update this :dag-map
            #(assoc-in % [(-> this :position) ::frequency] freq)))
  (min-direction [{:as this, :keys [position board-size]} sort-key]
    (first
     (apply min-key
            second
            (reduce-kv (fn [acc dir pos]
                         (if-let [child (get-in this [:dag-map pos])]
                           (assoc acc dir (sort-key child))
                           acc))
                       {}
                       (get-children-position-map position board-size)))))
  (-children [{:as this, :keys [position board-size]}]
    (->> (get-children-position-map position board-size)
         (u/map-map (-> this :dag-map)) ;; get associated data from dag-map         
         (u/filter-vals some?))) ;; remove nil values (directions with no node)

  (get-child [this direction]
    (update this :position
            #(ge/move-position % direction (-> this :board-size))))
  (update-children-in-direction [this direction f]
    (let [next-node
          (->DagNodeImpl
           (-> this :dag-map)
           (ge/move-position (-> this :position) direction (-> this :board-size))
           (-> this :board-size))]
      (assoc this :dag-map (-> (f next-node) :dag-map))))

  (-create-root-node [this children]
    (reduce update-this-dag-node this children)))

(defn dag-node
  "DagNodeImpl constructor"
  [game-state]
  (->DagNodeImpl
   {[0 0] (map->TreeExplorationNodeImpl {::frequency 0
                                         ::value ##Inf})}
   [0 0]
   (count (-> game-state ::gb/game-board))))

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
              (let [min-value (apply min (map value (vals (children node))))]
                (->> ge/directions
                     (filter #(= (-> node children % value) min-value))
                     g/rand-nth))))]
      
      (-> this
          (assoc :root-node (compute-root-node this world))
          (#(assoc % :next-movement (->  % :root-node random-min-direction)))))))
