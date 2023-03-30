(ns build
  "Build instructions for ken."
  (:require
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as d]))


(def basis (b/create-basis {:project "deps.edn"}))
(def lib-name 'com.amperity/ken)
(def base-version "1.2")

(def src-dir "src")
(def class-dir "target/classes")


(defn- lib-version
  "Construct the version of the library."
  [opts]
  (str base-version
       (if (:snapshot opts)
         "-SNAPSHOT"
         (str "." (b/git-count-revs nil)))))


(defn- jar-path
  "Construct the path to the jar artifact file."
  [opts]
  (format "target/%s-%s.jar" (name lib-name) (:version opts)))


;; ## Tasks

(defn clean
  "Remove compiled artifacts."
  [opts]
  (b/delete {:path "target"})
  opts)


(defn print-version
  "Print the current version of the library."
  [opts]
  (println (lib-version opts))
  opts)


(defn pom
  "Write out a pom.xml file for the project."
  [opts]
  (let [commit-sha (b/git-process {:git-args "rev-parse HEAD"})
        version (lib-version opts)]
    (b/write-pom
      {:basis basis
       :lib lib-name
       :version version
       :src-pom "doc/pom.xml"
       :src-dirs [src-dir]
       :class-dir class-dir
       :scm {:tag commit-sha}})
    (assoc opts
           :commit-sha commit-sha
           :version version
           :pom-file (b/pom-path
                       {:class-dir class-dir
                        :lib lib-name}))))


(defn jar
  "Build a JAR file for distribution."
  [opts]
  (let [opts (pom opts)
        jar-file (jar-path opts)]
    (b/copy-dir
      {:src-dirs [src-dir]
       :target-dir class-dir})
    (b/jar
      {:class-dir class-dir
       :jar-file jar-file})
    (assoc opts :jar-file jar-file)))


(defn install
  "Install a JAR into the local Maven repository."
  [opts]
  (let [opts (-> opts clean jar)]
    (b/install
      {:basis basis
       :lib lib-name
       :version (:version opts)
       :jar-file (:jar-file opts)
       :class-dir class-dir})
    opts))


(defn deploy
  "Deploy the JAR to Clojars."
  [opts]
  (let [opts (-> opts clean jar)]
    (d/deploy
      {:installer :remote
       :sign-releases? true
       :pom-file (:pom-file opts)
       :artifact (:jar-file opts)})
    opts))
