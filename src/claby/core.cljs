(ns ^:figwheel-hooks claby.core
  "Entry point to run the Claby game.

  See game.cljs for more about the game."
  (:require
   [goog.dom :as gdom]
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [claby.game :as g]
   [claby.game.generation :refer [create-nice-board]]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :refer [render]]))

(defonce game-size 27)

(defonce game-state (atom (g/init-game-state (create-nice-board game-size))))

;;; Player movement
;;;;;;

;;; Scroll if player moves too far up / down

(s/fdef board-scroll
  :args (s/cat :state ::g/game-state)
  :ret (s/or :double (s/double-in 0 1) :nil nil?))

(defn board-scroll
  "Returns the value of scroll needed so that player remains visible, as
  a fraction of the window height. If player is on top third of board,
  scroll to 0, on bottom-third, scroll to mid-page."
  [{:keys [::g/player-position ::g/game-board], :as state}]
  (let [size (count game-board)]
    (cond
      (< (player-position 0) (* size 0.4)) 0
      (> (player-position 0) (* size 0.7)) 0.5)))

(defn move-player
  "Moves player on the board by changing player-position"
  [e]
  (let [direction (case (.-key e)
                    ("ArrowUp" "e" "E") :up
                    ("ArrowDown" "d" "D") :down
                    ("ArrowLeft" "s" "S") :left
                    ("ArrowRight" "f" "F") :right
                    :no-movement)]
    (when (not= direction :no-movement)
      (.preventDefault e)
      (swap! game-state g/move-player direction)
      (when-let [scroll-value (board-scroll @game-state)]
        (.scroll js/window 0 (* scroll-value (.-innerHeight js/window)))))))

  
;;;
;;; Component & app rendering
;;;

(defn get-app-element []
  (gdom/getElement "app"))

(defn show-score
  [score]
  (if (pos? score) (.play (js/Audio. "coin.wav")))
  [:div.score [:span (str "Score : " score)]])

(defn claby []
  [:div#lapyrinthe.row.justify-content-md-center
   [:div.col.col-lg-2]
   [:div.col-md-auto
    [show-score (@game-state ::g/score)]
    (g/get-html-for-state @game-state)]
   [:div.col.col-lg-2]])

(def jq (js* "$"))
(defn mount [el]
  (let [gameMusic (js/Audio. "neverever.mp3")]
    (set! (.-loop gameMusic) true)
    (.addEventListener js/window "keydown" move-player)
    (.click (jq "#surprise img")
            (fn []
              (-> (.play gameMusic))
              (.fadeOut (jq "#surprise") 3000)
              (.click (jq "#surprise img") nil)))  
    (render [claby] el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(mount-app-element)


;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
