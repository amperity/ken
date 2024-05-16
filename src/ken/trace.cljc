(ns ken.trace
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
    [alphabase.bytes :as b]
    [alphabase.hex :as hex]
    [clojure.string :as str]
    [ken.event :as event]))


(def schemas
  "Event attributes for trace information as Malli-compatible schemas."
  {;; Unique identifier for the overall trace of all linked events.
   ::trace-id
   :string

   ;; Identifier for this specific span event.
   ::span-id
   :string

   ;; Identifier for the parent of this span.
   ::parent-id
   :string

   ;; A boolean to represent a sampling decision.
   ::keep?
   :boolean})


;; ## Trace Identifiers

(defn- gen-id
  "Generate a new trace or span identifier with `n` bytes of entropy."
  [n]
  (-> (b/random-bytes n)
      (hex/encode)
      (str/lower-case)))


(defn gen-trace-id
  "Generate a new trace identifier."
  []
  (gen-id 16))


(defn gen-span-id
  "Generate a new span identifier."
  []
  (gen-id 8))


;; ## Dynamic Tracing Context

(def ^:dynamic *data*
  "Dynamic context holding the data for the current trace. When inside a span,
  this should be bound to an atom containing a map with event data."
  nil)


(defmacro with-data
  "Evaluate the body with the tracing state bound to the given data."
  [data & body]
  `(binding [*data* ~data]
     ~@body))


(defn current-data
  "Return the current trace-id, span-id, and keep flag from the dynamic
  context, if any."
  []
  (some-> *data* deref (select-keys [::trace-id ::span-id ::keep?])))


(defn current-trace-id
  "Return the current trace-id from the trace context, if any."
  []
  (::trace-id (current-data)))


(defn current-span-id
  "Return the current span-id from the trace context, if any."
  []
  (::span-id (current-data)))


(defn child-attrs
  "Return a map of trace attributes for a new child of the current span, if
  any. Generates a new trace-id and span-id as necessary."
  ([]
   (child-attrs (current-data)))
  ([data]
   (merge
     {::trace-id (or (::trace-id data) (gen-trace-id))
      ::span-id (gen-span-id)}
     (when-let [parent-id (::span-id data)]
       {::parent-id parent-id})
     (when-some [keep? (::keep? data)]
       {::keep? keep?}))))


;; ## Sampling Logic

(defn sample?
  "Randomly decide whether to keep or drop a span using the given rate. A
  result of false means drop and a result of true means keep."
  [sample-rate]
  (zero? (rand-int sample-rate)))


(defn maybe-sample
  "Apply sampling logic to the event, returning an updated event map."
  [event]
  (cond
    ;; Sampling decision has already been made, so disregard any sample rate.
    (some? (::keep? event))
    (dissoc event ::event/sample-rate)

    ;; Sample rate is set without decision, so randomly sample. In the case
    ;; where we decide to keep the event, _do not_ set `::keep?` so that
    ;; descendents of this span can still perform their own sampling.
    (::event/sample-rate event)
    (if (false? (sample? (::event/sample-rate event)))
      (assoc event ::keep? false)
      event)

    ;; No sampling decision or rate, so return event as-is.
    :else
    event))


;; ## Header Propagation

;; These functions provide a way to propagate tracing information in an HTTP
;; header. This can be used to connect a trace on the front-end to spans in the
;; API server, or pass trace context among HTTP-based backend services in a
;; service-oriented architecture. Header values are versioned, and must start
;; with the string `{version}-`.

(def parent-header-name
  "Name of the header used to propagate OpenTelemetry trace information.

  See: https://www.w3.org/TR/trace-context/"
  "traceparent")


(defn format-parent-header
  "Construct a tracing header for inclusion in an HTTP request. Returns the
  formatted header string if the map of data provided has the necessary
  information, otherwise nil.

  See: https://www.w3.org/TR/trace-context/"
  [data]
  (when (and (not (str/blank? (::trace-id data)))
             (not (str/blank? (::span-id data))))
    (str "00-" (::trace-id data)
         "-" (::span-id data)
         "-" (if (::keep? data)
               "01"
               "00"))))


(defn parse-parent-header
  "Parse a parent tracing header from an HTTP request. Header values must start
  with `{version}-`. Returns a map of trace properties, or nil if the header is
  absent or unrecognized."
  [value]
  (when (and (not (str/blank? value))
             (str/starts-with? value "00-"))
    (let [[_ trace-id span-id flags-hex] (str/split value #"-" 4)]
      (when (and (= 32 (count trace-id))
                 (= 16 (count span-id))
                 (= 2 (count flags-hex)))
        (let [flags #?(:clj (try
                              (Integer/parseInt flags-hex 16)
                              (catch Exception _
                                0))
                       :cljs (let [v (js/parseInt flags-hex 16)]
                               (if (js/isNaN v) 0 v)))]
          (merge
            {::trace-id trace-id
             ::span-id span-id}
            (when (pos? (bit-and flags 0x01))
              {::keep? true})))))))


;; ### Legacy Headers

(def ^:deprecated header-name
  "Name of the header typically used to propagate Ken traces.

  DEPRECATED: Please switch to `parent-header-name`"
  "X-Ken-Trace")


(defn ^:deprecated format-header
  "Construct a tracing header for inclusion in an HTTP request. Returns the
  constructed header string if the map of data provided has at least a trace-id
  and span-id.

  DEPRECATED: Please switch to `format-parent-header`"
  [data]
  (let [trace-id (::trace-id data)
        span-id (::span-id data)]
    (when (and (not (str/blank? trace-id))
               (not (str/blank? span-id)))
      (str "0-" trace-id "-" span-id (when (::keep? data) "-k")))))


(defn- parse-legacy-header
  "Parse a legacy tracing header from an HTTP request. Header values must start
  with `{version}-`. Returns a map of trace properties, or nil if the header is
  absent or unrecognized."
  [value]
  (let [[_ trace-id span-id flags] (str/split value #"-")]
    (merge
      {::trace-id trace-id
       ::span-id span-id}
      (when (and flags (str/includes? flags "k"))
        {::keep? true}))))


(defn ^:deprecated parse-header
  "Parse a tracing header from an HTTP request. Header values must start with
  `{version}-`. Returns a map of trace properties, or nil if the header is
  absent or unrecognized.

  DEPRECATED: Please switch to `parse-parent-header`"
  [value]
  (cond
    ;; Nothing to parse.
    (str/blank? value)
    nil

    ;; Single digit version prefix means this is a legacy Ken header.
    (str/starts-with? value "0-")
    (parse-legacy-header value)

    ;; Double-digit hex version prefix means this is an OTel header.
    (str/starts-with? value "00-")
    (parse-parent-header value)))
