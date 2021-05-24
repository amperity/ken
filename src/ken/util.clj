(ns ^:no-doc ken.util
  "Utilities for the observability code."
  (:require
    [manifold.deferred :as d]))


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


(defn wrap-finally
  "Ensure the function `f` is run after the body run by `body-fn` completes. If
  `body-fn` returns a deferred vaule, `f` will run once it is realized."
  [body-fn f]
  (let [final (bound-fn* f)
        [result err] (try
                       [(body-fn) nil]
                       (catch Exception ex
                         [nil ex]))]
    (cond
      ;; An error was thrown synchronously, so invoke `final` and re-throw.
      err
      (do (final) (throw err))

      ;; A deferred value was returned. In this case, we need to ensure that
      ;; when the deferred is realized, the thread that delivers its value to
      ;; any chained callbacks retains the _original_ bindings from the context
      ;; where `wrap-finally` was called. Without this logic the trace bindings
      ;; wind up getting propagated incorrectly to chained functions.
      (d/deferred? result)
      (let [d' (d/deferred)]
        (d/on-realized
          result
          (bound-fn bound-success
            [x]
            (d/success! d' x))
          (bound-fn bound-error
            [e]
            (d/error! d' e)))
        (d/finally d' final))

      ;; A value was returned synchronously, so invoke `final` and return.
      :else
      (do (final) result))))
