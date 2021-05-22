(ns amperity.ken.context
  "Functions for collecting context from various sources and merging into a
  collected context map.

  Some example sources of context data:
  - environmental data about stack/sys/env/host names
  - request call graph context from prodigal
  - tracing span information"
  (:require
    [clojure.tools.logging :as log]))


(def ^:dynamic *local*
  "Dynamic context data bound in the local process. This is merged last into
  the collected context data, so it may override their entries."
  {})


(def ^:private collectors
  "An atom containing the map of registered context functions."
  (atom {}))


(defn register!
  "Register a function for collecting context data to include in events.

  The key uniquely identifies the function and will replace any existing
  collector; it may also be used to remove it later with `unregister!`."
  [k f]
  {:pre [(keyword? k) (fn? f)]}
  (swap! collectors assoc k f)
  nil)


(defn register-static!
  "Register the provided map of data as a static set of context attributes
  which apply to all events in the process. This is useful for common patterns
  such as environmental sources of context. Returns the key identifying the
  registered collector."
  ([data]
   (register-static! ::static data))
  ([k data]
   (let [ctx (into {} (remove (comp nil? val)) data)]
     (register! k (constantly ctx))
     k)))


(defn unregister!
  "Remove the identified function from the collection of context collectors."
  [k]
  (swap! collectors dissoc k)
  nil)


(defn clear!
  "Remove all registered context collectors."
  []
  (swap! collectors empty)
  nil)


(defn collect
  "Collect a merged context map by calling each registered context function."
  []
  (merge
    (reduce-kv
      (fn try-ctx
        [ctx k f]
        (try
          (conj ctx (f))
          (catch Exception ex
            (log/error ex "Unhandled failure in context collector" k)
            ctx)))
      {}
      @collectors)
    *local*))
