(ns mzero.ai.debug
  "NS with helpful debugging function for m00 player. Switch to the ns,
  start games and explore the player data easily."
  (:require [clojure.data.generators :as g]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.initialization :as mzi]
            [mzero.ai.ann.label-distributions :as mzld]
            [mzero.ai.ann.network :as mzn]
            [mzero.ai.main :as aim :refer [b curr-game pl w]]
            [mzero.ai.measure :as mzme]
            [mzero.ai.player :as aip]
            [mzero.ai.players.m0-modules.senses :as mzs]
            [mzero.ai.players.base :as mzb]
            [mzero.ai.train :as mzt :refer [default-board-size]]
            [mzero.ai.world :as aiw]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]
            [mzero.utils.random :refer [seeded-seeds]]))

(def default-opts
  {:seed 26
   :step-measure-fn mzme/step-measure
   :ann-impl {:layer-dims (repeat 2 512)
              :weights-generation-fn mzi/angle-sparse-weights
              :label-distribution-fn mzld/ansp}})

(defn plvz
  "Player visible zone"
  [{:as game-state, :keys [::gs/player-position]}]
  (-> game-state
      (update ::gb/game-board mzs/visible-matrix player-position)
      ;; convert from modsubvec to regular vec
      (update ::gb/game-board #(vec (map (comp vec seq) %)))
      (assoc ::gs/player-position [mzs/vision-depth mzs/vision-depth])
      gs/state->string))

(defn uk
  "Unqualified key: get the value for key even if map expects a
  qualified key and keyword is unqualified"
  [map_ keyword_]
  (let [qualified-keyword
        (first (filter #(= (name %) (name keyword_)) (keys map_)))]
    (get map_ qualified-keyword)))

(defn weights
  "Describe the first `nb-neurons` of layer `layer-index` by retaining
  their non-zeros weights (& indices), plus bias on top."
  ([{:as player :keys [ann-impl]} layer-index nb-neurons]
   (let [layer-index (mod layer-index (mzann/nb-layers ann-impl)) ;; allow negative indices
         get-non-nil-weights
         (fn [neuron-vector]
           (keep-indexed #(when (not (zero? %2)) (list (str %1) %2)) neuron-vector))
         bias (mzann/layer-data ann-impl layer-index "bias")]
     (->> (mzann/layer-data ann-impl layer-index "weight")
          (map get-non-nil-weights)
          (map #(cons (list "bias" %1) %2) bias)
          (take nb-neurons)
          (zipmap (map #(keyword (str "neuron-" %)) (range nb-neurons)))
          (into (sorted-map)))))
  ([player layer-index]
   (weights player layer-index 10)))

(defn values
  "Describe the first `nb` non-zeros inputs, outputs or raw-outputs, selected via
  `lkey` string of layer `layer-index` by retaining their non-nil
  weights (& indices), plus bias on top."
  ([{:as player :keys [ann-impl]} layer-index lkey nb]
   (let [layer-index (mod layer-index (mzann/nb-layers ann-impl)) ;; allow negative indices
         get-non-nil-vals
         (fn [val-vector]
           (keep-indexed #(when (not (zero? %2)) (list (str %1) %2)) val-vector))]
     (->> (mzann/layer-data ann-impl layer-index lkey)
          get-non-nil-vals
          (take nb))))
  ([player layer-index key]
   (values player layer-index key 10)))

(defn wrapped-measure-fn
  "Add move data for debbugging to the player's step-measure-fn"
  [step-measure-fn world player]
  (update (step-measure-fn world player) :moves
          conj {(:next-movement player) (values player -1 "raw-outputs")}))

(defn- get-moves
  ([start nb] (->> (pl) :moves reverse (drop start) (take nb)))
  ([nb] (->> (pl) :moves
             (cons {(-> (pl) :next-movement) (values (pl) -1 "raw-outputs")})
             (take nb)
             reverse))
  ([] (get-moves 5)))

(defn game-info
  [{{:keys [::gs/game-state ::aiw/game-step]} :world
    {:keys [:next-movement]} :player}]
  (format "%sStep %s -- Score %s\nLast move %s\n"
          (plvz game-state) game-step (-> game-state ::gs/score) next-movement))

(defn data
  "Shows data (see weights / values above) for move number `move-nb`"
  ([move-nb layer-idx lkey nb]
   (let [{:keys [:moves :ann-impl]} (pl)
         move-nb (mod move-nb (count moves))
         layer-idx (mod layer-idx (mzann/nb-layers ann-impl))
         player (-> moves reverse (nth move-nb) :player)]
     (println (game-info (-> moves reverse (nth move-nb))))
     (if (= lkey "weights")
       (weights player layer-idx nb)
       (values player layer-idx lkey nb))))
  ([move-nb layer-idx lkey]
   (data move-nb layer-idx lkey 10)))

(defn start
  [player-opts]
  (let [player-opts
         (-> (merge default-opts player-opts)
             (update :step-measure-fn #(partial wrapped-measure-fn %)))
         world-seed
         (binding [g/*rnd* (java.util.Random. (-> player-opts :seed))] (g/long))
         world (aiw/world default-board-size world-seed)
        player (aip/load-player "m00" player-opts world)]
    {:world world :player player}))

(defn start!
  ([player-opts]
   (reset! curr-game (start player-opts))
   (println (merge player-opts {:size default-board-size}))
   (println (aiw/data->string (-> @curr-game :world))))
  ([]
   (start {})))

(defn run-until
  ([pred? player world]
   (let [args (aim/parse-run-args "-v WARNING -n 1")
         run #(aim/run args (:world %) (:player %))
         run-all-steps?
         #(= mzt/nb-steps-per-game
             (- (-> % :world ::aiw/game-step) (-> world ::aiw/game-step)))]
     (->> (iterate run {:player player :world world})
          (filter (some-fn pred? run-all-steps?))
          first)))
  ([pred? player-opts]
   (let [{:keys [player world]} (start player-opts)]
     (run-until pred? player world))))

(defn run-until!
  ([pred?]
   (let [{:keys [player world]} @curr-game]
     (->> (run-until pred? player world)
          (reset! curr-game)
          game-info
          println)))
  ([pred? player-opts]
   (start! player-opts)
   (run-until! pred?)))

(defn n
  ([nb-steps]
   (let [step (get-in @curr-game [:world ::aiw/game-step])]
     (run-until! #(= (+ step nb-steps) (-> % :world ::aiw/game-step)))))
  ([] (n 1)))

(defn run-pred
  [{{{:keys [nb-moved-wall nb-next-wall]} :step-measurements} :player}]
  (when nb-moved-wall (< nb-next-wall nb-moved-wall)))

(def curr-seed (atom 0))

(defn debug-games
  "Run games as in mzt/run-games & mzt/continue-games, but with above
  debug commandes available after the run"
  ([opts nb-games seed]
   (log/info "Debuging games with options " opts)
   (reset! curr-seed seed)
   (let [player
         (-> (mzt/run-games opts 1 seed)
             (update ::mzb/step-measure-fn #(partial wrapped-measure-fn %)))
         world-seed (first (seeded-seeds seed nb-games 1))]
     (reset! curr-game {:player (mzt/continue-games player (dec nb-games) seed)
                        :world (aiw/world mzt/default-board-size world-seed)})))
  ([nb-games]
   (let [nb-played-games (count (-> @curr-game :player :game-measurements))
         player
         (mzt/continue-games (:player @curr-game) nb-games @curr-seed)
         world-seed
         (first (seeded-seeds @curr-seed (+ nb-played-games nb-games) 1))]
     (reset! curr-game {:player player
                        :world (aiw/world mzt/default-board-size world-seed)}))))

(s/fdef neural-branch
  :args (s/cat :inputs ::mzn/inputs :neuron-weights ::mzn/weight-row)
  :ret map?)

(defn neural-stem
  "Given `inputs` and a given outgoing neuron's `neural-weights`, return the
  incoming neurons that contributed most to its activation, along with
  a percentage of total contribution"
  ([inputs neural-weights]
   (let [contributions (map * neural-weights inputs)
         output (apply + contributions)
         total-contr (apply + (map #(Math/abs %) contributions))
         percentages
         (map #(/ (int (/ (Math/abs %) total-contr 0.0001)) 100.0) contributions)
         neural-branch-values
         (map vector
              (range (count neural-weights)) ;; indices
              neural-weights inputs contributions percentages)]
     (->> neural-branch-values
          (sort-by last >)
          (take 5)
          (map (partial zipmap [:index :weight :input :contribution :percentage]))
          (hash-map :value output :neural-branch))))
  ([{:as player :keys [ann-impl]} layer-idx neural-idx]
   (let [lidx (mod layer-idx (mzann/nb-layers ann-impl))
         weights-wo-bias (mzann/layer-data ann-impl lidx "weight")
         oidx (mod neural-idx (count weights-wo-bias))
         bias (nth (mzann/layer-data ann-impl lidx "bias") oidx)
         neuron-weights (conj (nth weights-wo-bias oidx) bias)
         inputs (mzann/layer-data ann-impl lidx "inputs")]
     (neural-stem inputs neuron-weights))))

(defn neural-branch-up
  "Given `weights` and an incoming `neural-index`, return the outgoing
  neurons it impacts most in terms of contribution percentage."
  ([weights neural-index]
   (let [percentage-of-weights
         (fn [neural-weights]
           (-> (Math/abs (nth neural-weights neural-index))
               (/ (apply + (map #(Math/abs %) neural-weights)) 0.0001)
               int (/ 100.0)))
         rank-in-weights
         (fn [neural-weights]
           (-> (map #(Math/abs %) neural-weights)
               (#(sort > %))
               vec
               (.indexOf (Math/abs (nth neural-weights neural-index)))))
         data-map
         (fn [outgoing-index neural-weights]
           {:outgoing-index outgoing-index
            :weight (nth neural-weights neural-index)
            :percentage (percentage-of-weights neural-weights)
            :rank (rank-in-weights neural-weights)})]
     (->> (map-indexed data-map weights)
          (sort-by :percentage >)
          (take 10))))
  ([{:as player :keys [ann-impl]} layer-index neural-index]
   (neural-branch-up
    (map conj (mzann/layer-data ann-impl layer-index "weight") (mzann/layer-data ann-impl layer-index "bias"))
    neural-index)))

(defn neural-branch-down
  "Given `neural-weights` of an outgoing neuron, return the incoming
  neurons impacting it most in terms of absolute weight."
  ([neural-weights]
   (let [percentage-of-weights
         (fn [weight]
           (-> (Math/abs weight)
               (/ (apply + (map #(Math/abs %) neural-weights)) 0.0001)
               int (/ 100.0)))
         rank-in-weights
         (fn [weight]
           (-> (map #(Math/abs %) neural-weights)
               (#(sort > %))
               vec
               (.indexOf (Math/abs weight))))
         data-map
         (fn [incoming-index weight]
           {:incoming-index incoming-index
            :weight weight
            :percentage (percentage-of-weights weight)
            :rank (rank-in-weights weight)})]
     (->> (map-indexed data-map neural-weights)
          (sort-by :percentage >)
          (take 10))))
  ([{:as player :keys [ann-impl]} layer-index neural-index]
   (neural-branch-down
    (nth
     (map conj
          (mzann/layer-data ann-impl layer-index "weight")
          (mzann/layer-data ann-impl layer-index "bias"))
     neural-index))))
