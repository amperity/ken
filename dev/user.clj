(ns user
  (:require
    [amperity.ken.context :as ctx]
    [amperity.ken.core :as ken]
    [amperity.ken.event :as event]
    [amperity.ken.tap :as tap]
    [amperity.ken.trace :as trace]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [manifold.deferred :as d]))
