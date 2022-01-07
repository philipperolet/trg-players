(ns mzero.ai.ann.shallow-mpann
  "Shallow Multiplayer ANN, and Mpann Manager.

  Permet d'avoir plusieurs agents utilisant un même ANN, de façon
  multithreadée synchrone. Prévu pour des joueurs `M00Player`.  On
  attribue à chaque agent un smpann, qui se comportera comme un
  ANN (même interface) et assurera le lien avec l'ANN sous-jacent.

  Les shallow MPANNS ne doivent pas être crées directement, mais via
  la fonction `shallow-mpanns`. Ils fonctionnent de pair avec un atom
  `mpann-manager-atom` qui permet leur synchronisation avec l'ANN
  sous-jacent.

  Chaque smpann a un champ vers cet atom, contenant l'ANN sous-jacent
  ainsi que d'autres éléments permettant la bonne exécution. Mettre
  l'ANN concret dans un atom garantit qu'on évite les conflits r/w sur
  celui-ci.

  ATTENTION: les agents **doivent** s'éxécuter en parallèle sans
  contrainte, car les shallow mpanns s'attendent.

  ATTENTION 2: si un des agents s'arrête (e.g. cas M00, parce que la
  partie est finie), les autres vont l'attendre indéfiniment

  Les agents doivent tous agir en parallèle chacun dans leur environnement,
  la même step en même temps. Chaque agent a un `index`, qui
  correspond à la position de son `input-vector` dans le batch et
  dans les activations intermédiaires et finales du réseau.

  Chaque agent donne son input, et lorsque tous les inputs sont
  donnés, la forward pass est exécutée. Donc à chaque step une forward
  pass s'exécute. On exécute par ailleurs au même moment la backward
  pass, s'il y a suffisamment d'exemples.

  Pour la backward pass, chaque agent empile son
  exemple (input-vector et target-distribution) lorsqu'il est dans une
  situation de renforcement--ce qui n'a pas lieu à toutes les
  steps. La backward pass s'exécutera lorsque le batch d'exemples
  atteint une taille suffisante, cf below.  Donc elle **NE s'exécutera
  PAS à chaque step**, contrairement à une exécution classique
  single-player de M00Player. Autre différence: il faut rejouer une
  passe forward avant de faire tourner la passe backward.

  `current-output-tensor`: afin d'éviter des transferts fréquents
  entre ANN sous-jacent, dont les tenseurs sont potentiellement sur
  GPU, et code des agents qui sont sur CPU, on copie l'output tensor
  intégralement lors de la forward pass. Les smpanns peuvent ensuite y
  accéder sans repasser par l'ANN sous-jacent.
  
  *Déterminisme et concurrence*

  Pour que l'exécution soit reproductible, il faut rendre l'exécution des 
  passes forward/backward suffisamment déterministe. C'est
  pourquoi on synchronise explicitement leur exécution; les agents
  s'attendent entre eux avant qu'elles démarrent, puis attendent
  qu'elles soient terminée avant de poursuivre leur exécution.

  Le fait qu'on attende que tout les agents soient dans le même
  état (fin de forward pass) pour exécuter la backward pass garantit
  que le batch reste le même pour 2 exécutions à mêmes paramètres. On
  sélectionne les `nb-smpanns` premiers éléments. Lorsque le nombre
  d'exemple disponible dépasse la taille de batch, et qu'on peut donc
  démarrer une passe, il faut que les exemples choisis pour celles-ci
  soient toujours les mêmes indépendamment de l'aléa du parallélisme:
  il faut que l'ordre des exemples de la backward pass soit également
  garanti. On trie donc via l'ordre d'arrivée des exemples et le
  current index, en utilisant la stabilité de la fonction `sort-by`."
  (:require [mzero.ai.ann.ann :as mzann]))

(defn- run-backward-pass!
  "Run backward pass in a deterministic fashion despite concurrency, see
  module doc"
  [mpann-manager]
  (let [[backward-batch remaining-datapoints & _]
        (->> mpann-manager :current-batch :backward
             (sort-by :index)
             (split-at (-> mpann-manager :nb-smpanns)))
        input-distribution-tensor (mapv :input-vector backward-batch)
        target-distribution-tensor (mapv :target-distribution backward-batch)
        discount-vector (mapv :discount-factor backward-batch)]
    (-> mpann-manager
        (update :ann-impl mzann/backward-pass!
                input-distribution-tensor
                target-distribution-tensor
                discount-vector)
        (assoc-in [:current-batch :backward] remaining-datapoints))))

(defn- run-backward-pass-if-ready!
  [{:as mpann-manager :keys [current-batch nb-smpanns]}]
  (let [ready-for-backward-pass?
        (<= nb-smpanns (count (-> current-batch :backward)))]
    (cond-> mpann-manager
      ready-for-backward-pass?
      run-backward-pass!)))

(defn- run-forward-pass!
  "Run forward pass. Input vectors for forward pass must be sorted by
  index so that a player may request the passes' output for itself
  using its index"
  [mpann-manager]
  (let [sorted-input-vectors
        (->> mpann-manager :current-batch :forward
             (sort-by :index)
             (mapv :input-vector))
        clear-forward-batch #(assoc-in % [:current-batch :forward] [])
        update-output-tensor
        #(assoc % :current-output-tensor (mzann/output (-> % :ann-impl)))]
    (-> (update mpann-manager :ann-impl mzann/forward-pass! sorted-input-vectors)
        update-output-tensor
        clear-forward-batch
        ;; save forward batch for backward pass
        (assoc :previous-forward-batch sorted-input-vectors))))

(defn- loop-until-passes-done!
  [mpann-manager-atom]
  (let [forward-pass-done? #(= 0 (count (-> % :current-batch :forward)))]
    (while (not (forward-pass-done? @mpann-manager-atom))
      (java.util.concurrent.locks.LockSupport/parkNanos 10000))))

(defn- add-input-to-forward-pass-batch [mpann-manager index input-vector]
  (update-in mpann-manager [:current-batch :forward]
             conj {:index index :input-vector input-vector}))

(defn- add-datapoint-to-backward-pass-batch
  [mpann-manager index target-distribution discount-factor]
  (let [datapoint
        {:index index
         :input-vector (-> mpann-manager :previous-forward-batch (nth index))
         :target-distribution target-distribution
         :discount-factor discount-factor}]
    (update-in mpann-manager [:current-batch :backward] conj datapoint)))

(defrecord ShallowMPANN [mpann-manager-atom index]
  mzann/ANN
  (-initialize [this _ _]
    (assoc this :label-distribution-fn
           (-> @mpann-manager-atom :ann-impl :label-distribution-fn)))
  
  (-forward-pass! [this input-tensor]
    (swap! mpann-manager-atom ;; input-tensor should have only 1 vector
           add-input-to-forward-pass-batch index (first input-tensor))
    (loop-until-passes-done! mpann-manager-atom)
    this)
  
  (-backward-pass! [this target-distribution-tensor discount-vector]
    (swap! mpann-manager-atom
           add-datapoint-to-backward-pass-batch
           index
           ;; target distrib tensor should be of size 1
           (first target-distribution-tensor)
           (first discount-vector))
    this)
  
  (-tens->vec [this tensor]
    (vec tensor))
  
  (nb-layers [this] (mzann/nb-layers (:ann-impl @mpann-manager-atom)))
  
  (-layer-data [this lindex lkey]
    (assert (and (= lkey "raw-outputs") (= lindex (dec (mzann/nb-layers this))))
            "Shallow MPANN does not support -layer-data except for
            final network output. Use underlying ANN instead.")
    (-> @mpann-manager-atom :current-output-tensor (nth index)
        ;; wrap since the output is supposed to be a tensor
        vector))
  
  (-act-fns [this] (mzann/-act-fns (:ann-impl @mpann-manager-atom))))

(defn- run-passes! [mpann-manager-atom mpann-manager]
  (reset! mpann-manager-atom
          (-> (run-forward-pass! mpann-manager)
              run-backward-pass-if-ready!)))

(defn- run-passes-when-ready!
  "Run passes when all smpanns gave their input vector to the forward
  batch. To ensure it is run only once, only occurs at the change that
  made the forward batch go from N-1 to N vectors."
  [_ mpann-manager-atom old-mpann-manager new-mpann-manager]
  (let [new-count (-> new-mpann-manager :current-batch :forward count)
        old-count (-> old-mpann-manager :current-batch :forward count)
        ready-for-forward-pass?
        (and (= (-> new-mpann-manager :nb-smpanns) new-count)
             (< old-count new-count))]
    (when ready-for-forward-pass?
      (future (run-passes! mpann-manager-atom new-mpann-manager)))))

(defn shallow-mpanns
  "Create a collection of shallow mpanns ready to work on the same
  underlying ANN"
  [nb-smpanns ann-impl]
  (let [mpann-manager-atom
        (atom {:nb-smpanns nb-smpanns
               :current-batch {:forward [] :backward []}
               :current-output-tensor []
               :ann-impl ann-impl})]
    (add-watch mpann-manager-atom :run-passes run-passes-when-ready!)
    (map #(mzann/-initialize (->ShallowMPANN mpann-manager-atom %) nil nil)
         (range nb-smpanns))))
