(ns mzero.ai.ann.activations
  "Various activation functions using neanderthal (and their derivatives):
  
   - `usual`, the initial function chosen for m0, equivalent to
  `trelu` with any value below `activation-threshold` nullified;
  - `relu` & `sigmoid` (well-known);
  - `trelu` for thresholded relu, min(relu, 1);
  - `spike`, a spike, centered on 0.5, with linear increase starting
  from 0.3 to 0.4, then a plateau, then linear decrease from 0.6 to 0.7.

  The derivative of activation fn named `afn!` is `afn-deriv` (without
  the bang since it does not change state)"
  (:require [mzero.ai.ann.ann :as mzann]
            [mzero.ai.ann.neanderthal-impl :as mzni]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.vect-math :as nvm]))

(defn- nullify-if-lower-than-thresh
  [ones matr]
  (->> (nc/axpy! (- mzann/activation-threshold)
                 (nc/submatrix ones (nc/mrows matr) (nc/ncols matr))
                 matr)
       nvm/ceil!))

(defn- temp-buffer-for-matr
  "Return a submatrix of buffer of same dims as `matr`"
  [buffer matr]
  (nc/submatrix buffer (nc/mrows matr) (nc/ncols matr)))

(defn usual!
  "Compute activation function of `outputs`, as 1_{x>s}*min(1,x) (s =
  activation-threshold)

  Since computation requires gpu-compatible vector ops, vectors of
  `zeros` and `ones` are required and should be provided by ann impl"
  ([{:as ann-impl :keys [zeros ones]} raw-outputs outputs]
   (let [dim (nc/mrows outputs)
         batch-size (nc/ncols outputs)
         threshold-output
         (partial nullify-if-lower-than-thresh ones)]    
     (-> raw-outputs
         (nvm/fmax! (nc/submatrix zeros dim batch-size) outputs)
         ;; only keep values above or equal to s
         threshold-output
         (nvm/mul! raw-outputs)
         (nvm/fmin! (nc/submatrix ones dim batch-size)))))
  ([{:as ann-impl :keys [layers]} layer-idx]
   (let [{:keys [::mzni/raw-outputs]} (nth layers layer-idx)]
     (update-in ann-impl [:layers layer-idx ::mzni/outputs]
                (partial usual! ann-impl raw-outputs)))))

(defn usual-deriv!
  "Compute of derivative of activation function : 1 if s <
  x < 1, 0 otherwise.

  Similarly to af!, need gpu-compatible ops. So this is computed as
  Math.ceil( (1-s)/2 - |x - (1+s)/2|) "
  ([{:as ann-impl :keys [ones zeros buffer]} matr]
   (let [ones (nc/submatrix ones (nc/mrows matr) (nc/ncols matr))
         zeros (nc/submatrix zeros (nc/mrows matr) (nc/ncols matr))
         halfsplus1 (/ (inc mzann/activation-threshold) 2)
         half1minuss (/ (- 1 mzann/activation-threshold) 2)]    
     (->> (nc/copy! matr (temp-buffer-for-matr buffer matr))
          (nc/axpy! (- halfsplus1) ones)
          nvm/abs!
          (nc/axpby! half1minuss ones -1.0)
          (#(nvm/fmax! % zeros))
          nvm/ceil!)))
  ([matr]
   (usual-deriv! {:zeros (nn/fge (nc/mrows matr) (nc/ncols matr))
                  :ones (nc/entry! (nn/fge (nc/mrows matr) (nc/ncols matr)) 1)
                  :buffer (nn/fge (nc/mrows matr) (nc/ncols matr))}
                matr)))

(def usual {::mzann/af! usual! ::mzann/af-deriv! usual-deriv!})

(defn relu!
  ([{:as ann-impl :keys [zeros]} raw-outputs outputs]
   (let [dim (nc/mrows outputs)
         batch-size (nc/ncols outputs)]    
     (-> raw-outputs
         (nvm/fmax! (nc/submatrix zeros dim batch-size) outputs))))
  ([{:as ann-impl :keys [layers]} layer-idx]
   (let [{:keys [::mzni/raw-outputs]} (nth layers layer-idx)]
     (update-in ann-impl [:layers layer-idx ::mzni/outputs]
                (partial relu! ann-impl raw-outputs)))))

(defn relu-deriv!
  ([{:as ann-impl :keys [ones zeros buffer]} matr]
   (let [ones (nc/submatrix ones (nc/mrows matr) (nc/ncols matr))
         zeros (nc/submatrix zeros (nc/mrows matr) (nc/ncols matr))]    
     (->> (nc/copy! matr (temp-buffer-for-matr buffer matr))
          (nvm/fmin! ones)
          (nvm/fmax! zeros)
          nvm/ceil!))))

(def relu {::mzann/af! relu! ::mzann/af-deriv! relu-deriv!})

(defn sigmoid!
  ([{:as ann-impl :keys [zeros]} raw-outputs outputs]
   (nvm/sigmoid! raw-outputs outputs))
  ([{:as ann-impl :keys [layers]} layer-idx]
   (let [{:keys [::mzni/raw-outputs]} (nth layers layer-idx)]
     (update-in ann-impl [:layers layer-idx ::mzni/outputs]
                (partial sigmoid! ann-impl raw-outputs)))))

(defn sigmo-deriv!
  ([{:as ann-impl :keys [ones zeros buffer]} matr]
   (let [ones (nc/submatrix ones (nc/mrows matr) (nc/ncols matr))
         zeros (nc/submatrix zeros (nc/mrows matr) (nc/ncols matr))]    
     (->> (nvm/sigmoid! matr (temp-buffer-for-matr buffer matr))
          (nvm/mul! (nvm/sigmoid! (nc/scal -1.0 matr)))))))

(def sigmoid {::mzann/af! sigmoid! ::mzann/af-deriv! sigmo-deriv!})

(defn trelu!
  ([{:as ann-impl :keys [zeros ones]} raw-outputs outputs]
   (let [dim (nc/mrows outputs)
         batch-size (nc/ncols outputs)]    
     (-> raw-outputs
         (nvm/fmax! (nc/submatrix zeros dim batch-size) outputs)
         (nvm/fmin! (nc/submatrix ones dim batch-size) outputs))))
  ([{:as ann-impl :keys [layers]} layer-idx]
   (let [{:keys [::mzni/raw-outputs]} (nth layers layer-idx)]
     (update-in ann-impl [:layers layer-idx ::mzni/outputs]
                (partial trelu! ann-impl raw-outputs)))))  

(defn trelu-deriv!
  "f=1_{0 < x < 1} Computed through 1-|x-0.5|"
  [ann-impl matr]
  (->> (nc/copy! matr (temp-buffer-for-matr (:buffer ann-impl) matr))
       (trelu! ann-impl matr)
       nvm/frac!
       nvm/ceil!))

(def trelu {::mzann/af! trelu! ::mzann/af-deriv! trelu-deriv!})

(defn spike!
  "Spike function, f(x)=0 up to 0.3, linear up to f(0.4)=1, then f(x)=1
  up to 0.6, then down linear until f(0.7)=0
  Formula is f(x)=min(1, 2 - |5-10x|)"
  ([{:as ann-impl :keys [ones zeros]} raw-outputs outputs]
   (let [dim (nc/mrows outputs)
         batch-size (nc/ncols outputs)]    
     (->> (nc/copy! raw-outputs outputs)
          (nc/axpby! 5.0 (nc/submatrix ones dim batch-size) -10.0)
          nvm/abs!
          (nc/axpby! 2.0 (nc/submatrix ones dim batch-size) -1.0)
          (#(nvm/fmin! % (nc/submatrix ones dim batch-size)))
          (#(nvm/fmax! % (nc/submatrix zeros dim batch-size))))))
  ([{:as ann-impl :keys [layers]} layer-idx]
   (let [{:keys [::mzni/raw-outputs]} (nth layers layer-idx)]
     (update-in ann-impl [:layers layer-idx ::mzni/outputs]
                (partial spike! ann-impl raw-outputs)))))

(defn spike-deriv!
  "f(x)=1 for 0.3 < x < 0.4 and 0.6 < x < 0.7, f(x)=0 otherwise"
  [ann-impl matr]
  (->> (nc/copy matr)
       (spike! ann-impl matr)
       nvm/frac!
       nvm/ceil!))

(def spike {::mzann/af! spike! ::mzann/af-deriv! spike-deriv!})
