(ns mzero.ai.players.dag-node
  "DagNodeImpl: nodes are shallow, no data except for position information on the
  board (incl. dag map & board size). Data is held by the `dag-map`"
  (:require [mzero.game.events :as ge]
            [mzero.game.board :as gb]
            [mzero.ai.players.tree-exploration :as te]
            [clojure.data.generators :as g]))

(def get-children-position-map
  (memoize
   (fn [position board-size]
     (reduce #(assoc %1 %2 (ge/move-position position %2 board-size))
             {}
             ge/directions))))

(defn- get-node-to-aggregate [direction1 direction2 children]
  (let [opposed-directions {:up :down, :down :up, :right :left, :left :right}]
    (-> children
        direction1
        (te/get-descendant [(opposed-directions direction1) direction2]))))

(defn- compute-merged-node-in-direction [direction children]
  (let [compute-frequency
        (fn [child-dir]
          (reduce
           #(+ %1 (or (te/frequency (get-node-to-aggregate %2 child-dir children)) 0))
           0 ge/directions))
        compute-value
        (fn [child-dir]
          (reduce #(min %1 (or (te/value (get-node-to-aggregate %2 child-dir children)) ##Inf))
                  ##Inf
                  ge/directions))]
    (te/map->TreeExplorationNodeImpl
     {::te/frequency (compute-frequency direction)
      ::te/value (compute-value direction)})))

(defrecord DagNodeImpl [dag-map position board-size]
  te/TreeExplorationNode

  (append-child [this direction]
    (let [child-position
          (ge/move-position (-> this :position) direction (-> this :board-size))
          init-child-if-nil
          (fn [nd]
            (if nd nd (te/map->TreeExplorationNodeImpl
                       {::te/frequency 0
                        ::te/value ##Inf})))]
      (update this :dag-map #(update-in % child-position init-child-if-nil))))
  
  (-value [{:keys [dag-map], [x y] :position}]
    (::te/value ((dag-map x) y)))
  (-assoc-value [{:as  this, [x y] :position} val_]
    (update this :dag-map
            #(assoc-in % [x y ::te/value] val_)))
  (-frequency [{:keys [dag-map], [x y] :position}]
    (::te/frequency ((dag-map x) y)))
  (-assoc-frequency [{:as  this, [x y] :position} freq]
    (update this :dag-map
            #(assoc-in % [x y ::te/frequency] freq)))
  (min-direction [{:as this, :keys [position board-size]} sort-key]
    (let [positions-map
          (get-children-position-map position board-size)
          get-value-from-position
          #(some-> (((-> this :dag-map) (% 0)) (% 1)) sort-key)
          trim-nil
          #(if (nil? %) ##Inf %)]
      (apply min-key
             #(-> %
                  positions-map
                  get-value-from-position
                  trim-nil)
             (if (:random-min @te/tuning)
               (g/shuffle ge/directions)
               ge/directions))))
  
  (-children [{:as this, :keys [position board-size]}]
    (let [positions-map
          (get-children-position-map position board-size)
          add-child-from-direction
          #(let [[x y] (positions-map %2)]
             (if-let [child (((-> this :dag-map) x) y)]
               (assoc %1 %2 child)
               %1))]
      (add-child-from-direction
       (add-child-from-direction
        (add-child-from-direction
         (add-child-from-direction {} :up)
         :right)
        :down)
       :left)))

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
    (assoc (te/->TreeExplorationNodeImpl)
           ::te/value 0
           ::te/frequency Integer/MAX_VALUE
           ::te/children
           (reduce #(assoc %1 %2 (compute-merged-node-in-direction %2 children))
                   {}
                   ge/directions))))

(defn dag-node
  "DagNodeImpl constructor"
  [game-state]
  (->DagNodeImpl
   (assoc-in (vec (repeat (count (::gb/game-board game-state))
                          (vec (repeat (count (::gb/game-board game-state)) nil))))
             [0 0]
             (te/map->TreeExplorationNodeImpl {::te/frequency 0
                                               ::te/value ##Inf}))
   [0 0]
   (count (-> game-state ::gb/game-board))))
