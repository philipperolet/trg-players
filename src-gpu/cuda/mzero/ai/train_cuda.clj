(ns mzero.ai.train-cuda
  "Function to run m0 with cuda computation.

  Separated from train.clj so it can be loaded at runtime rather than
  'compile-time'.

  Having it loaded at compile time requires install of cuda libs
  even if they are not used in fine."
  (:require [uncomplicate.clojurecuda.core :as ucu]
            [uncomplicate.neanderthal.cuda :as ncu]
            [uncomplicate.commons.core :refer [with-release]]))

(defn- cuda-computation-setup
  "Cuda computations may require explicitly setting the context. This is
  because contexts are bound to the CPU thread in which they are
  initially created. In multithreaded programs, computations may occur
  in different threads so we must explicitly ensure the context stays the same
  accross threads."
  [ctx & args]
  (ucu/current-context! ctx))

(defn run-cuda
  "Run a function whose first arg is a computation mode object, with
  required data to perform CUDA GPU computations."
  [fn_ & args]
  (ucu/with-default
    (let [ctx (ucu/current-context)]
      (with-release [factory
                     (ncu/cuda-float ctx ucu/default-stream)]
        (apply fn_
               {:type :gpu-cuda
                :factory factory
                :computation-setup-fn (partial cuda-computation-setup ctx)}
               args)))))
