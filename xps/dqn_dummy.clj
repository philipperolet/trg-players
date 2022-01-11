(ns dqn-dummy
  "Compare various layer dims for dqn dummy params"
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [mzero.ai.train :as mzt :refer [run-games]]
            [mzero.utils.xp :as xp]
            [mzero.utils.plot :refer [plot]]
            [mzero.utils.utils :as u]
            [mzero.ai.players.m0-modules.senses :as mzs]))

(def seed 26)
(def layer-dimss [(repeat 2 512) (repeat 2 2048) (repeat 3 512) (repeat 3 2048)])
(defn -main
  "Run xp for chosen params, with each time `nb-games` for learning"
  [nb-games computation-mode]
  (print "[")
  (doseq [layer-dims layer-dimss]
    (let [nb-games (read-string nb-games)
          computation-mode (read-string computation-mode)
          player-opts
          {:computation-mode computation-mode
           :layer-dims layer-dims}
          xp-options
          {:seed seed
           :nb-games nb-games
           :label-distr-fn "ansp"
           :player-opts
           (dissoc player-opts :weights-generation-fn :act-fns :label-distribution-fn :step-measure-fn)}]
      (log/info "Starting with options: " xp-options)
      (let [[timing player] (u/timed
                             (run-games player-opts nb-games seed
                                        (partial mzt/initial-players "m0-dqn")))
            time-per-game (/ (int (/ timing nb-games 0.1)) 10000)]
        (->> player
             :game-measurements
             (hash-map :xp-options xp-options
                       :time-per-game time-per-game
                       :measures)
             pprint))))
  (print "]"))

(defn parse-all
  [measure-key nb-games computation-mode]
  (let [commit "1f5a4ad"
        outfile
        (format "xps/results/%s-%s-%s-%s-%s.out"
                (ns-name *ns*) nb-games computation-mode "" commit)
        title (str (ns-name *ns*))
        remove-nil-elements
        (fn [xp-data] (update xp-data :measures #(remove nil? %)))
        raw-data 
        (->> (read-string (slurp outfile))
             (map #(update % :measures (partial map measure-key)))
             (map remove-nil-elements))
        window-size 50
        measures-count (-> raw-data first :measures count (- window-size))
        line-name-of-xp
        #(str "layer-dims=" (nth layer-dimss %))
        mean
        (fn [data-row i]
          (->> data-row
               (drop i)
               (take window-size)
               xp/mean))
        windowed-points-for-xp
        (fn [{:keys [measures]}]
          (map (partial mean measures) (range measures-count)))
        xp-names (map line-name-of-xp (range (count layer-dimss)))
        chart-data-for-xp
        #(hash-map :line-name (line-name-of-xp %1)
                   :points (windowed-points-for-xp %2))
        whole-data (map-indexed chart-data-for-xp raw-data)
        plot-data
        (map (fn [i]
               (reduce #(assoc %1 (:line-name %2) (-> %2 :points (nth i))) {}
                       whole-data))
             (range measures-count))
        layer-dim-label-fn (fn [label-name] [label-name #(get % label-name)])]
    (apply plot title plot-data (map layer-dim-label-fn xp-names))))

