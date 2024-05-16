(ns ken.event
  "Base event attributes and functions.")


;; ## Event Attributes

(def schemas
  "Event attributes for span information, as Malli-compatible schemas."
  {;; Time the observed event began.
   ::time
   [:fn inst?]

   ;; Log-style level for the significance of the event.
   ::level
   [:enum :trace :debug :info :warn :error :fatal]

   ;; Human-friendly label for the event - like the function or method being called.
   ::label
   :string

   ;; Longer form message string for the event.
   ::message
   :string

   ;; Boolean indicating whether the event records a fault, meaning an unexpected
   ;; error occurred during the event. This should be set to true for "server"
   ;; errors (5xx), but not for validation or other "client" errors (4xx) where
   ;; the service operated normally.
   ::fault?
   :boolean

   ;; An exception that occurred during the processing of the span.
   ::error
   [:fn (partial instance? #?(:clj Throwable
                              :cljs js/Error))]

   ;; Namespace the event was sent from.
   ::ns
   :symbol

   ;; Line in the file the event was sent from.
   ::line
   [:int {:min 0}]

   ;; Thread name.
   ::thread
   :string

   ;; Duration (in milliseconds) the event covers.
   ::duration
   [:number {:min 0}]

   ;; An optional sample rate to apply to an event and its children.
   ;; A sample rate of n will send 1/n events onward.
   ::sample-rate
   [:int {:min 1}]

   ;; Overall event data map.
   "Event"
   [:map
    ::time
    ::level
    ::label
    [::message {:optional true}]
    [::fault? {:optional true}]
    [::error {:optional true}]
    [::ns {:optional true}]
    [::line {:optional true}]
    [::thread {:optional true}]
    [::duration {:optional true}]
    [::sample-rate {:optional true}]]})


;; ## Utility Functions

(defn now
  "Return the current instant. This can be overridden for testing purposes."
  []
  #?(:clj (java.time.Instant/now)
     ;; TODO: should this be goog.DateTime instead?
     :cljs (js/Date.)))
