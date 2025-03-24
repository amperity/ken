(ns ^:no-doc ken.util
  "Utilities for the observability code."
  (:import
    clojure.lang.Var
    (java.util.concurrent
      CompletableFuture
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


(defmacro ^:private when-manifold
  "Compile-time support for the manifold async library. Returns nil if manifold
  is not available."
  [& body]
  (try
    (require 'manifold.deferred)
    `(do ~@body)
    (catch Exception _
      nil)))


(defn wrap-finally
  "Ensure the function `f` is run after the body run by `body-fn` completes. If
  `body-fn` returns an asynchronous value that supports callbacks, `f` will run
  once it is realized. `f` must not throw exceptions."
  [body-fn f]
  (let [[result err] (try
                       [(body-fn) nil]
                       (catch Exception ex
                         [nil ex]))]
    (cond
      ;; An error was thrown synchronously, so invoke `f` and re-throw.
      err
      (do
        (f)
        (throw err))

      ;; A manifold deferred value was returned, so construct a new deferred value
      ;; that the final logic can be triggered from. We also need to ensure
      ;; that any further callbacks operate with the same thread bindings as
      ;; the location `wrap-finally` was invoked. Without this logic the trace
      ;; bindings may be propagated incorrectly to chained functions.
      (when-manifold
        (manifold.deferred/deferred? result))
      (when-manifold
        (let [ret (manifold.deferred/deferred)]
          (manifold.deferred/on-realized
            result
            (bound-fn bound-success
              [x]
              (f)
              (manifold.deferred/success! ret x))
            (bound-fn bound-error
              [ex]
              (f)
              (manifold.deferred/error! ret ex)))
          ret))

      ;; A Java async stage or completable future was returned, so attach a
      ;; callback to execute the final logic. As above, this needs to propagate
      ;; thread bindings.
      (instance? CompletionStage result)
      (let [bindings (get-thread-bindings)
            cf ^CompletableFuture (if (instance? CompletableFuture result)
                                    (.newIncompleteFuture ^CompletableFuture result)
                                    (CompletableFuture.))]
        (.whenComplete
          ^CompletionStage result
          (reify BiConsumer
            (accept
              [_ value ex]
              (push-thread-bindings bindings)
              (try
                (f)
                (if ex
                  (.completeExceptionally cf ex)
                  (.complete cf value))
                (finally
                  (pop-thread-bindings))))))
        cf)

      ;; NOTE: if a regular old `future` was returned, we could wrap it in
      ;; another thread waiting for it to complete, but that seems like it
      ;; would be a pretty surprising resource allocation for an observability
      ;; library to make. Maybe reconsider when virtual threads are the norm?

      ;; A value was returned synchronously, so invoke `f` and return.
      :else
      (do
        (f)
        result))))
