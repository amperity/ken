(ns ken.event
  "Base event attributes and functions."
  (:require
    [clojure.spec.alpha :as s]))


;; ## Event Specs

;; Time the observed event began.
(s/def ::time inst?)


;; Log-style level for the significance of the event.
(s/def ::level #{:trace :debug :info :warn :error :fatal})


;; Human-friendly label for the event - like the function or method being
;; called.
(s/def ::label string?)


;; Longer description string for the event.
(s/def ::message string?)


;; Boolean indicating whether the event records a fault, meaning an unexpected
;; error occurred during the event. This should be set to true for "server"
;; errors (5xx), but not for validation or other "client" errors (4xx) where
;; the service operated normally.
(s/def ::fault? boolean?)


;; An exception that occurred during the processing of the span.
(s/def ::error
  (partial instance? #?(:clj Throwable
                        :cljs js/Error)))


;; Namespace the event was sent from.
(s/def ::ns symbol?)


;; Line in the file the event was sent from.
(s/def ::line nat-int?)


;; Thread name.
(s/def ::thread string?)


;; Duration (in milliseconds) the event covers.
(s/def ::duration (s/and number? (complement neg?)))


;; An optional sample rate to apply to an event and its children.
;; A sample rate of n will send 1/n events onward.
(s/def ::sample-rate nat-int?)


;; Overall event data map.
(s/def ::data
  (s/keys :req [::time
                ::level
                ::label]
          :opt [::message
                ::fault?
                ::error
                ::ns
                ::line
                ::thread
                ::duration
                ::sample-rate]))


;; ## Utility Functions

(defn now
  "Return the current instant. This can be overridden for testing purposes."
  []
  #?(:clj (java.time.Instant/now)
     ;; TODO: should this be goog.DateTime instead?
     :cljs (js/Date.)))
