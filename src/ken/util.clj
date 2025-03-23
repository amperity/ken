(ns ^:no-doc ken.util
  "Utilities for the observability code."
  (:import
    clojure.lang.Var
    (java.util.concurrent
      CompletionStage)
    (java.util.function
      BiConsumer)))


(defn current-thread-name
  "Return the name of the current thread."
  []
  (.getName (Thread/currentThread)))


(defn stopwatch
  "Create a delay which will yield the number of milliseconds elapsed between
  its creation and the time it is realized."
  []
  (let [start (System/nanoTime)]
    (delay (/ (- (System/nanoTime) start) 1e6))))


(let [top-frame (delay
                  (let [p (promise)]
                    (doto (Thread.
                            #(deliver p (Var/getThreadBindingFrame))
                            "get-top")
                      (.start))
                    @p))]
  (defn get-top
    []
    @top-frame))


(defn debug-thread-bindings
  [& msgs]
  (let [thread (Thread/currentThread)
        top (get-top)
        prev-field (doto (.getDeclaredField clojure.lang.Var$Frame "prev")
                     (.setAccessible true))
        frames (loop [frames (list (Var/getThreadBindingFrame))]
                 (if-let [prev (.get prev-field (first frames))]
                   (recur (conj frames prev))
                   frames))
        names (mapv (fn [frame]
                      (if (identical? frame top)
                        "TOP"
                        (format "%08X" (hash frame))))
                    frames)]
    (printf "[%s] %s :: %s\n"
            (.getName thread)
            (clojure.string/join " » " names)
            (clojure.string/join " " msgs))
    (flush)))


(defn wrap-finally
  "Ensure the function `f` is run after the body run by `body-fn` completes. If
  `body-fn` returns an asynchronous value, `f` will run once it is realized."
  [body-fn f]
  (let [final (bound-fn* f)
        [result err] (try
                       [(body-fn) nil]
                       (catch Exception ex
                         [nil ex]))]
    (cond
      ;; An error was thrown synchronously, so invoke `final` and re-throw.
      err
      (do
        (final)
        (throw err))

      ;; A native Java async stage was returned, so attach a when-complete
      ;; callback to execute the final logic. We also need to ensure that any
      ;; further callbacks operate with the same thread bindings as the
      ;; location `wrap-finally` was invoked. Without this logic the trace
      ;; bindings may be propagated incorrectly to chained functions.
      (instance? CompletionStage result)
      ;; FIXME: this is the wrong approach
      (let [stage ^CompletionStage result
            bindings (Var/getThreadBindingFrame)]
        (.whenComplete
          stage
          (reify BiConsumer
            (accept
              [_ _ _]
              (debug-thread-bindings "in whenComplete BiConsumer")
              (Var/resetThreadBindingFrame bindings)
              (final)
              (debug-thread-bindings "did whenComplete BiConsumer")))))

      ;; A value was returned synchronously, so invoke `final` and return.
      :else
      (do
        (final)
        result))))
