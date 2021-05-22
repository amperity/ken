ken
===

[![CircleCI](https://circleci.com/gh/amperity/ken.svg?style=shield&circle-token=15e84dcf80db1a201e8113388c86da9dbd5223d4)](https://circleci.com/gh/amperity/ken)
[![codecov](https://codecov.io/gh/amperity/ken/branch/master/graph/badge.svg)](https://codecov.io/gh/amperity/ken)
[![cljdoc badge](https://cljdoc.org/badge/amperity/ken)](https://cljdoc.org/d/amperity/ken/CURRENT)

> [ken](https://www.wordnik.com/words/ken)
> - _noun_ Perception; understanding.
> - _noun_ View; sight.
> - _intransitive verb_ To have knowledge or an understanding.

This library provides a set of general-purpose
[observability](https://en.wikipedia.org/wiki/Observability) tools which can be
used to instrument your code in order to better understand it.

In particular, it attempts to satisfy the following goals:
- Define a general "observability event" shape decoupled from any one consumer.
- Be able to report events from anywhere in the code, without worrying about
  wiring in a component dependency.
- Provide an open extension point for collecting rich context data from an
  event's source. Don't couple to sources.
- Provide an open extension point for subscribing to observability events.
  Don't couple to sinks.
- Support distributed tracing by tracking trace/span identifiers and context.
- Build up events over time with annotations.


## Concepts

There are a few concepts which `ken` works with that need to be understood to
use the tools effectively.

### Events

An _event_ is a map of Clojure keys and values which represent a thing that
happened in your code. Events will typically contain a selection of keys from
`amperity.ken.event` to provide a basic foundation:

```clojure
{:amperity.ken.event/time #inst "2020-03-27T21:22:27.003Z",
 :amperity.ken.event/duration 44.362681,
 :amperity.ken.event/label "the thing",
 :amperity.ken.event/message "Perform some routine activity",
 :amperity.ken.event/ns amperity.foo.thing,
 ,,,}
```

Here we have an event about some process which started at the given time,
lasted 44.3 milliseconds, and had some associated human-friendly metadata.
Events may have many other attributes as well, including authentication
context, custom identifiers, inputs and outputs, and more.

### Contexts

A _context collector_ in `ken` is a source of contextual information used to
enrich the events being observed. For example, you might want to pull some
information out of the local system properties:

```clojure
(defn user-context
  []
  {::local-user (System/getProperty "user.name")})

(amperity.ken.context/register! :user user-context)
```

After this, all events observed by `ken` will have the `::local-user` key
populated by default. Other contextual sources could include properties about
the running process such as the build number, environment, current heap size,
etc.

Contexts can be removed with `unregister!` and the keyword id, or reset
entirely with `clear!`.

If you have a service-oriented architecture, another rich source of context
data can be the _broadcast context_ which is transmitted through the whole
request graph. This usually includes information about the authenticated user
making the request, the account it is for, and other widely-relevant info.

### Subscriptions

This library builds on the idea of the
[tap](https://github.com/clojure/clojure/blob/master/changes.md#23-tap)
introduced in Clojure 1.10. This is a global queue which may be sent events
from anywhere in the code. In order for those events to be _useful_, they must
be handled by functions which are subscribed to the tap.

As an example, we can [pretty-print](https://github.com/greglook/puget) all
events to our console for inspection:

```clojure
(amperity.ken.tap/subscribe! :cprint puget.printer/cprint)
```

Subscribed functions will be called with every event sent to the tap and should
not block for significant periods of time or they may cause event loss.
Subscriptions can be removed with `unsubscribe!` or reset entirely with
`clear!`.

### Tracing

Finally, `ken` uses a standard model for distributed tracing of events. Events
are grouped together under a top-level _trace_ which identifies an entire unit
of work. Each event within this may be a _span_ which represents a subunit of
work covering some duration. Spans may be children of other spans, meaning they
represent more fine-grained bits of work in turn. Linking all the spans in a
trace together forms a tree, sometimes called a "call graph" which represents
the observations collected about the unit of work which was traced.

Using the library will automatically capture and extend the tracing identifiers
where needed, which show up in the observed events:

```clojure
{:amperity.ken.event/time #inst "2020-03-27T21:22:27.003Z",
 :amperity.ken.event/duration 44.362681,
 :amperity.ken.trace/trace-id "bplzs2gajkfcbojkspx7",
 :amperity.ken.trace/span-id "cuoclyafu4",
 ,,,}

{:amperity.ken.event/time #inst "2020-03-27T21:22:27.005Z",
 :amperity.ken.event/duration 41.805794,
 :amperity.ken.trace/trace-id "bplzs2gajkfcbojkspx7",
 :amperity.ken.trace/parent-id "cuoclyafu4",
 :amperity.ken.trace/span-id "cjatpftw5j",
 ,,,}
```

Above are two related spans, the second nested inside the first.


## Usage

Enough theory, how do you actually use this? Releases are published on Clojars;
to use the latest version with Leiningen, add the following to your project
dependencies:

[![Clojars Project](http://clojars.org/amperity/ken/latest-version.svg)](http://clojars.org/amperity/ken)

```clojure
(require
  '[amperity.ken.core :as ken]
  '[amperity.ken.event :as event])
```

### Direct Observation

The most direct way to use the library is to call the `observe` macro in your
code in order to send events.

```clojure
(ken/observe {::event/label "a thing", ::my/key 123})
```

This will collect the local context, add it to the event, then send it to the
tap for publishing. By default, this returns immediately (non-blocking) but you
can specify a timeout in milliseconds if you would like to wait for the event
to be accepted. Either way, this returns true if the event was queued and false
if it was rejected.

### Watching Spans

An extremely common way to generate events is by describing [spans](#tracing)
which cover some work happening. The library offers the `watch` macro for this:

```clojure
(ken/watch "a thing happening"
  (crunch-numbers 2.17 3.14)
  (think-heavily "what am I?"))
```

This will instrument the body of expressions and observes an event at the end
which includes tracing and timing information. The `watch` form will return the
value of the final expression. This also works for [asynchronous
values](https://github.com/clj-commons/manifold), so the following code will
only record the event once the deferred completes:

```clojure
(ken/watch "another thing"
  (d/chain
    (d/future
      (crunch-numbers 8675309))
    ,,,))
```

For richer event data, you can specify a map - the string versions above
automatically expand into `:amperity.ken.event/label` entries:

```clojure
(ken/watch {::event/label "foo the bar"
            ::foo 123
            ::bar 'baz}
  (foo-bar! bar))
```

All of the provided keys will be present in the final event.

### Annotations

Finally, you can _annotate_ enclosing spans by adding additional properties to
the events. When code is executing inside a `watch`, you can use the `annotate`
and `time` tools:

```clojure
(ken/watch "a thing"
  (try
    (when (foo? x)
      (ken/annotate {::foo? true}))
    (ken/time ::thinking
      (think-heavily "what is consciousness?"))
    (catch Exception ex
      (ken/annotate {::error ex}))))
```

This would produce a span event labeled `"a thing"` with a few potential
additional attributes - a `::foo?` key set to true, an `::error` key with a
caught exception, and a `::thinking` key holding the number of milliseconds
spent in the `think-heavily` call.

### Sampling

Sampling is the act of selecting a subset of events from a large collection of
events. Not everything needs to be sampled, but if you have high frequency
events and _most_ of them are very similar, sampling them can be a good way to
reducing your total event volume.

Sampling is controlled by two tracing keys, which can be specified in the
initial `ken/watch` or in a later `ken/annotate` call.

In order to opt into sampling for a specific span, you can seet the
`:amperity.ken.event/sample-rate` key. This is an integer value `n` that will
cause, on average, about `1/n` of the events to be sampled. The rest will be
marked to be discarded by consumers.

When a span has been marked for sampling, it will set the second tracing key
`:amperity.ken.trace/keep?` on the resulting event. The keep key can have one
of three possible states:

- `nil` or absent: The span will be kept and forwarded along. This is the
  default behavior.
- `false`: The span will be marked to be dropped and the decision will
  propagate down to its child spans.
- `true`: The span and its children will be kept. This decision overrides
  sampling logic in child spans.

Note that the keep key can be set directly; for example, if you encounter an
error and want to ensure that a span and its (subsequent) children are
recorded, you can use `annotate` to set `keep?` to true.

For additional reading on sampling best practices, see
[Honeycomb's article](https://docs.honeycomb.io/working-with-your-data/best-practices/sampling/)
on the topic.


## Integrations

**TODO:** link to other libs


## License

Copyright © 2021 Amperity, Inc.

Distributed under the MIT License.
