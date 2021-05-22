(ns amperity.ken.core
  (:refer-clojure :exclude [time])
  (:require
    [amperity.ken.context :as ctx]
    [amperity.ken.event :as event]
    [amperity.ken.tap :as tap]
    [amperity.ken.trace :as trace]
    [amperity.ken.util :as ku]
    [clojure.string :as str]))


(defmacro with-context
  "Evaluate the body of expressions with the given data merged into the local
  context."
  [ctx & body]
  `(binding [ctx/*local* (merge ctx/*local* ~ctx)]
     ~@body))


(defmacro observe
  "Observe an event about the system. Returns true if the event was accepted or
  false if it was dropped.

  By default this returns immediately. If a timeout is given, this will block
  until the event is accepted or the timeout expires."
  ([data]
   `(observe ~data nil))
  ([data timeout-ms]
   `(when-let [data# ~data]
      (let [event# (merge
                     (ctx/collect)
                     {::event/time (ku/now)
                      ::event/level :info
                      ::event/thread (ku/current-thread-name)
                      ::event/ns '~(symbol (str *ns*))}
                     ~(when-let [line (:line (meta &form))]
                        {::event/line line})
                     (when-let [trace-id# (trace/current-trace-id)]
                       {::trace/trace-id trace-id#})
                     data#)]
        (tap/send event# ~timeout-ms)))))


(defmacro watch
  "Wrap a span event observation around a body of expressions.

  The `data` provided may be a string or a keyword, in which case it will be
  used directly as the event's label. To pass additional event attributes, you
  can instead provide a map (which must include a value for `::event/label`).

      ;; shorthand
      (ken/watch \"amperity.my.code/do-the-thing\"
        ,,,)

      ;; shorthand with auto-resolved keyword
      (ken/watch ::do-the-thing
        ,,,)

      ;; expanded form
      (ken/watch {::ken.evt/label \"amperity.my.code/do-the-thing\"
                  ::something 123
                  ,,,}
        ,,,)

  The provided data will be merged into the base event, which will also include
  timing and tracing properties."
  [data & body]
  `(let [elapsed# (ku/stopwatch)
         data# (merge {::event/level :info}
                      (ctx/collect)
                      ~(if (or (string? data) (keyword? data))
                         {::event/label data}
                         data)
                      {::event/time (ku/now)
                       ::event/thread (ku/current-thread-name)
                       ::event/ns '~(symbol (str *ns*))}
                      ~(when-let [line (:line (meta &form))]
                         {::event/line line}))
         state# (atom (trace/watch-span data#))]
     (ku/wrap-finally
       (fn body#
         []
         (trace/with-data state#
           ~@body))
       (fn report#
         []
         (let [event# (assoc @state# ::event/duration @elapsed#)]
           (tap/send event# nil))))))


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
                                     ::event/duration]))]
    (throw (ex-info
             (str "Attempting to set event attributes which cannot be changed by annotations: "
                  (str/join " " (map name forbidden)))
             {:keys (vec forbidden)})))
  (when (thread-bound? #'trace/*data*)
    (when-let [trace trace/*data*]
      (swap! trace merge data))))


(defn error
  "Annotate the enclosing span with an error that was encountered during
  execution. Sets the exception under the `:amperity.ken.event/error` key
  and sets `:amperity.ken.event/fault?` to true."
  [ex]
  (annotate {::event/fault? true
             ::event/error ex}))


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
