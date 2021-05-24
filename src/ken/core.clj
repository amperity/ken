(ns ken.core
  "Core user API namespace for ken instrumentation.

  There are really three significant phases where data is collected to form an
  event or 'span':
  1. At the _beginning_ of a span, to initialize the event data:
     - base: label, time, level
     - callsite: ns, line
     - trace: trace-id, parent-id, span-id
     - sampling: sample-rate, keep?
  2. _During_ a span:
     - using annotate, error, and time
  3. At the _end_ of a span:
     - context collection
     - thread name
     - duration

  An `observe` call performs the first and third phases back-to-back and
  immediately sends an event, while `watch` will wrap a body of code, providing
  time to enrich it with extra data in phase two."
  (:refer-clojure :exclude [time])
  (:require
    [clojure.string :as str]
    [ken.context :as ctx]
    [ken.event :as event]
    [ken.tap :as tap]
    [ken.trace :as trace]
    [ken.util :as ku]))


;; ## Attribute Collection

(defn- callsite-attrs
  "Collect call-site information for inclusion in an event. Note that this is
  called by macro, so it emits code forms."
  [form]
  (let [line (:line (meta form))]
    (cond-> {}
      *ns*
      (assoc ::event/ns (list 'quote (ns-name *ns*)))

      line
      (assoc ::event/line line))))


(defn ^:no-doc create-span
  "Collect information and build an initial map of event data for a span. Any
  provided data is merged into the result."
  [data]
  (let [event (merge
                {::event/time (event/now)
                 ::event/level :info}
                (if (or (string? data) (keyword? data))
                  {::event/label data}
                  data))]
    (if (keyword? (::event/label event))
      ;; Convert keyword labels into strings.
      (update event ::event/label #(subs (str %) 1))
      event)))


(defn ^:no-doc enrich-span
  "Enrich a span by collecting attribute information from the contextual
  environment."
  [data]
  (merge (ctx/collect)
         {::event/thread (ku/current-thread-name)}
         data))


(defmacro with-context
  "Evaluate the body of expressions with the given data merged into the local
  context."
  [ctx & body]
  `(binding [ctx/*local* (merge ctx/*local* ~ctx)]
     ~@body))


;; ## Event Observation

(defmacro observe
  "Observe an event about the system. Returns true if the event was accepted or
  false if it was dropped.

  By default this returns immediately. If a timeout is given, this will block
  until the event is accepted or the timeout expires."
  ([data]
   `(observe ~data nil))
  ([data timeout-ms]
   `(let [event# (-> (create-span ~data)
                     (merge
                       ~(callsite-attrs &form)
                       (trace/child-attrs))
                     (trace/maybe-sample)
                     (enrich-span))]
      (tap/send event# ~timeout-ms))))


(defmacro watch
  "Wrap a span event observation around a body of expressions.

  The `data` provided may be a string or a keyword, in which case it will be
  used directly as the event's label. To pass additional event attributes, you
  can instead provide a map (which must include a value for `::event/label`).

      ;; shorthand
      (ken/watch \"my.code/do-the-thing\"
        ,,,)

      ;; shorthand with auto-resolved keyword
      (ken/watch ::do-the-thing
        ,,,)

      ;; expanded form
      (ken/watch {::ken.evt/label \"my.code/do-the-thing\"
                  ::something 123
                  ,,,}
        ,,,)

  The provided data will be merged into the base event, which will also include
  timing and tracing properties."
  [data & body]
  `(let [elapsed# (ku/stopwatch)
         data# (-> (create-span ~data)
                   (merge
                     ~(callsite-attrs &form)
                     (trace/child-attrs))
                   (trace/maybe-sample))
         state# (atom data#)]
     (ku/wrap-finally
       (fn body#
         []
         (trace/with-data state#
           ~@body))
       (fn report#
         []
         (let [event# (-> @state#
                          (assoc ::event/duration @elapsed#)
                          (enrich-span))]
           (tap/send event# nil))))))


;; ## Event Annotation

(defn annotate
  "Add fields to the enclosing span event."
  [data]
  (when-not (map? data)
    (throw (IllegalArgumentException.
             (str "Annotation data must be a map, got: "
                  (pr-str (class data))))))
  (when-let [forbidden (seq (filter (partial contains? data)
                                    [::event/time
                                     ::event/label
                                     ::event/ns
                                     ::event/line
                                     ::event/thread
                                     ::event/duration
                                     ::trace/trace-id
                                     ::trace/parent-id
                                     ::trace/span-id]))]
    (throw (ex-info
             (str "Attempting to set event attributes which cannot be changed by annotations: "
                  (str/join " " (map name forbidden)))
             {:keys (vec forbidden)})))
  (when (thread-bound? #'trace/*data*)
    (when-let [trace trace/*data*]
      (swap! trace merge data))))


(defmacro time
  "Time the given body of expressions and annotate the enclosing span with the
  duration in milliseconds under the given key."
  [event-key & body]
  {:pre [(keyword? event-key)]}
  `(let [elapsed# (ku/stopwatch)]
     (ku/wrap-finally
       (fn body#
         []
         ~@body)
       (fn report#
         []
         (annotate {~event-key @elapsed#})))))


(defn error
  "Annotate the enclosing span with an error that was encountered during
  execution. Sets the exception under the `:ken.event/error` key
  and sets `:ken.event/fault?` to true."
  [ex]
  (when-not (instance? Throwable ex)
    (throw (IllegalArgumentException.
             "error annotations must be throwables")))
  (annotate {::event/fault? true
             ::event/error ex}))
