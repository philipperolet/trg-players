(ns mzero.utils.plot
  (:require [incanter.core :refer [view]]
            [incanter.charts :refer [line-chart add-categories]]
            [mzero.utils.xp :as xp]))

(defn plot
  "Given a list of `measures`, plots `yfn`(measures(i)) for i from 0 to
  |measures|, labeled `label`
  Multiple series are allowed"
  [title measures [label yfn] & label-yfns]
  (let [initial-chart
        (line-chart (range (count measures))
                    (map yfn measures)
                    :title title
                    :x-label "Game number"
                    :legend true
                    :points true
                    :series-label label)
        add-serie
        (fn [chart [label_ fn_]]
          (add-categories chart
                          (range (count measures))
                          (map fn_ measures)
                          :series-label label_))]
    (view (reduce add-serie initial-chart label-yfns))))

(defn plot-training [player measure-key]
  (let [title (str measure-key)
        measures
        (->> player :game-measurements
             (map measure-key)
             (remove nil?))
        window-size 50
        measures-count (-> measures count (- window-size))
        mean
        (fn [data-row i]
          (->> data-row
               (drop i)
               (take window-size)
               xp/mean))
        windowed-points-for-xp
        (fn [measures]
          (map (partial mean measures) (range measures-count)))
        plot-data (windowed-points-for-xp measures)]
    (plot title plot-data ["Current Player" identity])))
