(ns ^:figwheel-hooks claby.ux.base
  "Main UX components to run the Claby game."
  (:require
   [goog.dom :as gdom]
   [clojure.string :as cstr :refer [split]]
   [clojure.test.check]
   [clojure.test.check.properties]
   [cljs.spec.alpha :as s]
   [cljs.spec.gen.alpha :as gen]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :refer [render]]
   [claby.game.board :as gb]
   [claby.game.state :as gs]
   [claby.game.events :as ge]
   [claby.game.generation :as gg]))

(defonce game-size 15)

(defonce levels
  [{:message "Lapinette enceinte doit manger un maximum de fraises"
    ::gg/density-map {:fruit 5
                      :cheese 0}}
   {:message "Attention au fromage non-pasteurisé !"
    ::gg/density-map {:fruit 5
                      :cheese 3}
    :message-color "darkgoldenrod"}
   {:message "Evite les apéros alcoolisés"
    ::gg/density-map {:fruit 5
                      :cheese 3}
    :message-color "darkblue"
    :enemies [:drink :drink]}
   {:message "Les souris ont infesté la maison!"
    ::gg/density-map {:fruit 5
                      :cheese 3}
    :message-color "darkmagenta"
    :enemies [:drink :mouse :mouse]}
   {:message "Le covid ça fait peur!"
    ::gg/density-map {:fruit 5
                      :cheese 3}
    :message-color "darkcyan"
    :enemies [:virus :virus]}
   {:message "Allez on arrête de déconner."
    ::gg/density-map {:fruit 5
                      :cheese 5}
    :message-color "darkgreen"
    :enemies [:drink :drink :virus :virus :mouse :mouse]}])

(defonce game-state (atom {}))
(defonce level (atom 0))

(defn parse-params
  "Parse URL parameters into a hashmap"
  []
  (let [param-strs (-> (.-location js/window) (split #"\?") last (split #"\&"))]
    (into {} (for [[k v] (map #(split % #"=") param-strs)]
               [(keyword k) v]))))


;;; Player movement
;;;;;;

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
      (swap! game-state ge/move-player direction))))
  
;;;
;;; Component & app rendering
;;;

(defprotocol ClapyUX
  "Required UX functions to run Lapyrinthe."

  (init [this] "Setup at the very beginning")
  
  (start-level [this] "Every time a level begins")
  
  (animate-transition [this transition-type]
    "Animation when the game status changes. 3 possible transitions may occur:
   - nextlevel (when level is won but there are next levels);
   - over (when game is lost);
   - won (when the last level is won).")

  (score-update [this score] "Every time the score updates :)")
  
  (enemy-style [this type]
    "How to style enemies (string with css style for the enemy type)"))

(defonce jq (js* "$"))

(defn add-enemies-style
  [ux enemies]
  (.remove (jq "#app style"))
  (doall (map-indexed  
          #(.append (jq "#app")
                    (str "<style>#lapyrinthe table td.enemy-" %1 " "
                         (enemy-style ux (name %2))
                         "</style>"))
          enemies)))


(defonce game-step (atom 0))
(defonce enemy-move-interval {:drink 4 :mouse 2 :virus 1})
(defn move-enemies []
  (swap! game-step inc)
  (when (= :active (@game-state ::gs/status))
    (->> (get-in levels [@level :enemies])
         (keep-indexed #(if (= 0 (mod @game-step (enemy-move-interval %2))) %1))
         (reduce ge/move-enemy-random @game-state)
         (reset! game-state))))

(defn start-game
  [ux]
  (.addEventListener js/window "keydown" move-player)
  (add-enemies-style ux (get-in levels [@level :enemies]))
  (.show
   (jq "#loading")
   200
   (fn []
     (swap! game-state #(gg/create-nice-game
                         game-size
                         (levels @level)))
     (.hide (jq "#loading") 200)
     (.setInterval js/window move-enemies
                   (int (get (parse-params) :tick "130")))
     (start-level ux))))

(defn game-transition
  "Component rendering a change in game status. 3 possible transitions may occur:
   - nextlevel (when level is won but there are next levels);
   - over (when game is lost);
   - won (when the last level is won)."
  [ux status]
  
  ;; Get transition type from game status
  (when-let [transition-type
             (case status
               :won (if (< (inc @level) (count levels))
                      :nextlevel
                      :won)
               :over :over
               nil)]

    ;; render animation and component
    (.removeEventListener js/window "keydown" move-player)
    (if (= transition-type :nextlevel)
      (swap! level inc))
    (animate-transition ux transition-type)
    [:div]))

(defn show-score
  [ux score]
  (score-update ux score)
  [:div.score
   [:span (str "Score: " score)]
   [:br]
   [:span (str "Level: " (inc @level))]])

(defn claby [ux]
  (if (re-find #"Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini"
               (.-userAgent js/navigator))
    [:h2.subtitle "Le jeu est prévu pour fonctionner sur ordinateur (mac/pc)"]

    [:div#lapyrinthe.row.justify-content-md-center
     [:h2.subtitle [:span (get-in levels [@level :message])]]
     [:div.col.col-lg-2]
     [:div.col-md-auto
      [show-score ux (@game-state ::gs/score)]
      [:table (gs/get-html-for-state @game-state)]]
   [:div.col.col-lg-2]
   [game-transition ux (@game-state ::gs/status)]]))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app

(defn mount-app-element []
  (when-let [el (gdom/getElement "app")]             
    (render [claby] el)))

(defn run-game
  "Runs the Lapyrinthe game with the specified UX. There must be an 'app' element in the html page."
  [ux]
  {:pre [(gdom/getElement "app")]}
  (reset! level (int (get (parse-params) :cheatlev "0")))
  (init ux)
  (render [claby ux] (gdom/getElement "app")))

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
