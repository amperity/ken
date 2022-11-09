(defproject com.amperity/ken "1.1.1-SNAPSHOT"
  :description "Observability library to serve as integration with a variety of event outputs."
  :url "https://github.com/amperity/ken"
  :license {:name "MIT License"
            :url "https://mit-license.org/"}

  :plugins
  [[lein-cloverage "1.2.2"]]

  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [mvxcvi/alphabase "2.1.1"]
   [manifold "0.2.4"]]

  :profiles
  {:repl
   {:source-paths ["dev"]
    :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
    :dependencies
    [[org.clojure/tools.namespace "1.3.0"]]}})
