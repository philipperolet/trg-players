(ns mzero.ai.players.activation
  "Details in arbre & version docs.

  Note: for performance, lots of computations alter
  matrices/vectors in place."
  (:require [uncomplicate.neanderthal
             [core :as nc]
             [vect-math :as nvm]]
            [clojure.spec.alpha :as s]
            [mzero.ai.players.common :refer [ones zeros-matr zeros]]
            [mzero.ai.players.network :as mzn]))

(def s "Activation threshold" 0.2)
(def decrease-factor (- (/ (- 1.0 s) s)))

(defn- af!
  "Computes activation function, see arbre & version docs"
  [outputs]
  (let [dim (nc/dim outputs)
        nullify-if-lower-than-s
        #(nvm/mul! % (nvm/ceil! (nc/axpy (- (* 0.999 s)) (ones dim) %)))]    
    (-> outputs
        (nvm/fmin! (ones dim))
        (nvm/fmax! (zeros dim))
        ;; only keep values above or equal to s
        nullify-if-lower-than-s)))

(s/fdef simultaneous-forward-pass!
  :args (-> (s/cat :layers ::mzn/layers
                   :inputs (s/every ::mzn/neural-value)
                   :plusb? (s/? boolean?))
            (s/and (fn [{:keys [inputs layers]}]
                     (comment "Input dimension fits first layer")
                     (= (count inputs) (nc/dim (::mzn/inputs (first layers)))))))
  :ret ::mzn/layers)

(defn sequential-forward-pass!
  "Run the network of `layers` using an initial seq `inputs`. Return the
  `outputs` of the last layer. Almost everything in `layers` is
  changed. 

  For each layers it will compute weights * input and run the
  activation function. If called with flag `plusb?` on, before
  processing a layer, it will reset its input's last element to
  1. This allows computation of `w*x+b` rather than `w*x`, provided
  the rest of the code is aware that inputs' last elements are
  dedicated to this use.

  Note : activation function is not applied to last layer.
  
  This pass is deemed sequential because layers are computed in turn
  from first to last, with each layer using the new value of its
  previous layer's output, rather than being computed all at once,
  using their previous layer's old output."
  ([layers inputs plusb?]
   (nc/transfer! inputs (-> layers first ::mzn/inputs))
   (doseq [layer layers] (nc/scal! 0 (-> layer ::mzn/outputs)))
   (let [wtimesx!
         (fn [{:keys [::mzn/inputs ::mzn/weights ::mzn/outputs] :as layer}]
           (nc/mv! (nc/trans weights)
                   (if plusb? (nc/entry! inputs (dec (nc/dim inputs)) 1.0) inputs)
                   outputs))]
     (doall (map (comp af! wtimesx!) (butlast layers)))
     (wtimesx! (last layers))))
  ([layers inputs]
   (sequential-forward-pass! layers inputs false)))
