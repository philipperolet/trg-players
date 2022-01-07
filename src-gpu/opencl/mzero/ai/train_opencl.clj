(ns mzero.ai.train-opencl
  "Function to run m0 with opencl computation.

  Separated from train.clj so it can be loaded at runtime rather than
  'compile-time'.

  Having it loaded at compile time requires install of opencl libs
  even if they are not used in fine."
  (:require [uncomplicate.clojurecl.core :as ucl]
            [uncomplicate.neanderthal.opencl :as ncl]
            [uncomplicate.commons.core :refer [with-release]]))

(defn run-opencl
  "Run a function whose first arg is a computation mode object, with
  required data to perform OpenCL GPU computations."
  [fn & args]
  (ucl/with-default
    (with-release [factory
                   (ncl/opencl-float ucl/*context* ucl/*command-queue*)]
      (apply fn {:type :gpu-opencl
                 :factory factory} args))))
