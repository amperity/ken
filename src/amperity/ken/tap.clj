(ns amperity.ken.tap
  "Observability instrumentation using a global 'tap' which can have events
  reported to it from any location in the code. This draws inspiration from
  Clojure's `tap>`, but is more directly targeted at instrumentation."
  (:refer-clojure :exclude [send])
  (:require
    [clojure.tools.logging :as log])
  (:import
    (java.util.concurrent
      ArrayBlockingQueue
      BlockingQueue
      TimeUnit)))


;; ## Subscriptions

(def ^:private subscriptions
  "An atom containing the map of subscribed functions."
  (atom {}))


(defn subscribe!
  "Subscribe a function to be called with all events sent to the tap. Returns
  the key `k`.

  The key uniquely identifies the function and will replace any existing
  subscription; it may also be used to remove it later with `unsubscribe!`."
  [k f]
  {:pre [(keyword? k) (fn? f)]}
  (swap! subscriptions assoc k f)
  k)


(defn unsubscribe!
  "Remove the identified function from the tap subscriptions."
  [k]
  (swap! subscriptions dissoc k)
  nil)


(defn clear!
  "Remove all subscriptions from the tap."
  []
  (swap! subscriptions empty)
  nil)


;; ## Event Publishing

(def ^:private ^BlockingQueue event-queue
  "A queue of events which have been sent but not published yet."
  (ArrayBlockingQueue. 1024))


(def ^:private publisher
  "Background thread which takes events from the queue and publishes them to
  subscribed functions. The thread is only started once the first event has
  been sent."
  (delay
    (doto (Thread.
            (fn send-loop
              []
              (let [event (.take event-queue)]
                (run!
                  (fn publish
                    [[k f]]
                    (try
                      (f event)
                      (catch Throwable ex
                        (log/error ex "Unhandled failure in tap function" k))))
                  @subscriptions)
                (recur)))
            "amperity.ken.tap/publisher")
      (.setDaemon true)
      (.start))))


(defn send
  "Send the given event to be published to any subscribed tap functions.
  Returns true if the event was accepted or false if it was dropped.

  By default this returns immediately. If a timeout is given, this will block
  until the event is accepted or the timeout expires."
  ([event]
   (send event nil))
  ([event timeout-ms]
   (when event
     (force publisher)
     (if timeout-ms
       (.offer event-queue event timeout-ms TimeUnit/MILLISECONDS)
       (.offer event-queue event)))))


(defn queue-size
  "Return the number of events currently in the queue."
  []
  (.size event-queue))


(defn drain!
  "Drain the current event-queue to empty it. Mostly useful for testing."
  []
  (.clear event-queue)
  nil)
