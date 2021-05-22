(ns amperity.ken.trace
  "Tracing support for instrumenting code.

  Trace data is structured as events with a few special properties:
  - `::trace/trace-id`
    The parent identifier for the overall trace of all linked events.
  - `::trace/span-id`
    A unique identifier for this event.
  - `::trace/parent-id`
    The span which is the parent of this one.
  - `::trace/keep?`
    A boolean to represent a sampling decision for events sent to ken on whether to keep or drop the spans of a trace.
    - The absence of this key means the observed event will be kept and forwarded along. This is the default state.
    - `false` means to discard a span. This decision will be propagated to child spans, but can be overridden later by a child span that decides to force keep.
    - `true` means to forcibly keep a span and all of its child spans, ignoring all other sampling decisions in the subtree.

  Additionally, trace events must have:
  - `::event/time`
    This indicates the instant the span started.
  - `::event/duration`
    The duration (in milliseconds) the span covers."
  (:require
    [alphabase.base32 :as b32]
    [alphabase.bytes :refer [random-bytes]]
    [amperity.ken.event :as event]
    #?(:clj [amperity.ken.util :as ku])
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))


;; Unique identifier for the overall trace of all linked events.
(s/def ::trace-id string?)


;; Identifier for this specific span event.
(s/def ::span-id string?)


;; Identifier for the parent of this span.
(s/def ::parent-id string?)


;; A boolean to represent a sampling decision.
(s/def ::keep? boolean?)


(def ^:dynamic *data*
  "Dynamic context holding the data for the current trace. When inside a span,
  this should be bound to an atom containing a map with event data."
  nil)


(defmacro with-data
  "Evaluate the body with the tracing state bound to the given data."
  [data & body]
  `(binding [*data* ~data]
     ~@body))


(defn current-trace-id
  "Return the current trace-id from the trace context, if any."
  []
  (some-> *data* deref ::trace-id))


(defn current-span-id
  "Return the current span-id from the trace context, if any."
  []
  (some-> *data* deref ::span-id))


(defn current-keep?
  "Return the current sampling decision, if any."
  []
  (some-> *data* deref ::keep?))


(defn sample?
  "Randomly decide whether to keep or drop a span using the given rate. A
  result of false means drop and a result of true means keep."
  [sample-rate]
  (zero? (rand-int sample-rate)))


(defn- gen-id
  "Generate a new trace or span identifier with `n` bytes of entropy."
  [n]
  (-> (random-bytes n)
      (b32/encode)
      (str/lower-case)))


(defn new-span
  "Generate the base data for a new span from the current tracing context."
  ([]
   (new-span (current-trace-id) (current-span-id) (current-keep?)))
  ([trace-id span-id keep?]
   (merge
     ;; TODO: automatically set time in cljs
     {#?@(:clj [::event/time (ku/now)])
      ::trace-id (or trace-id (gen-id 12))
      ::span-id (gen-id 6)}
     (when span-id
       {::parent-id span-id})
     (when (some? keep?)
       {::keep? keep?}))))


(defn watch-span
  "Prepare a new span for use within the `watch` macro. This generally
  shouldn't be called directly."
  [data]
  (when-not (::event/label data)
    (throw (ex-info "Watch span data must include an event label"
                    {:data data})))
  (let [event (merge (new-span) data)]
    (cond-> event
      ;; Convert keyword labels into strings.
      (keyword? (::event/label event))
      (update ::event/label #(subs (str %) 1))

      ;; Sampling decision has already been made, so disregard any sample rate.
      (some? (::keep? event))
      (dissoc ::event/sample-rate)

      ;; Sample rate is set without decision, so randomly sample.
      (and (nil? (::keep? event))
           (::event/sample-rate event)
           (false? (sample? (::event/sample-rate event))))
      (assoc ::keep? false))))
