(ns batch-influence
  "Compare various batch sizes (with different seedings too)"
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [mzero.ai.train :as mzt :refer [run-games]]
            [mzero.utils.xp :as xp]
            [mzero.utils.plot :refer [plot]]
            [mzero.utils.utils :as u]))

(def seed 26)
(def batch-sizes [1 4 16 64 128])
(defn -main
  "Run xp for chosen params, with each time `nb-games` for learning"
  [nb-games computation-mode]
  (print "[")
  (doseq [batch-size batch-sizes]
    (let [nb-games (read-string nb-games)
          computation-mode (read-string computation-mode)
          #_batch-size #_(read-string batch-size)
          player-opts
          {:computation-mode computation-mode
           :layer-dims (repeat 2 2048)
           :batch-size batch-size}
          xp-options
          {:seed seed
           :nb-games nb-games
           :label-distr-fn "ansp"
           :player-opts
           (dissoc player-opts :weights-generation-fn :act-fns :label-distribution-fn :step-measure-fn)}
          wrap-if-one
          #(if (= 1 batch-size) (vector %) %)]
      (log/info "Starting with options: " xp-options)
      (let [[timing players] (u/timed (run-games player-opts nb-games seed))
            time-per-game (/ (int (/ timing nb-games 0.1)) 10000)]
        (->> players
             wrap-if-one
             (map :game-measurements)
             (apply interleave)
             (hash-map :xp-options xp-options
                       :time-per-game time-per-game
                       :measures)
             pprint))))
  (print "]"))

(defn parse-all
  [measure-key nb-games computation-mode]
  (let [commit "be95304"
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
        #(str "Batch-size=" (nth batch-sizes %))
        mean
        (fn [data-row i]
          (->> data-row
               (drop i)
               (take window-size)
               xp/mean))
        windowed-points-for-xp
        (fn [{:keys [measures]}]
          (map (partial mean measures) (range measures-count)))
        xp-names (map line-name-of-xp (range (count batch-sizes)))
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

