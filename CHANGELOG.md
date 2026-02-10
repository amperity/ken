Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

...


## [2.1.55] - 2026-02-10

- Change in how sampling works. `ken.trace/maybe-sample` now renames `:ken.event/sample-rate` to `:ken.trace/upstream-sampling`
  so that it can be used by ken tap subscribers. [PR#13](https://github.com/amperity/ken/pull/13)


## [2.0.50] - 2025-03-24

This PR fixes an issue in 2.x where, in very specific circumstances, the
thread-binding machinery would throw an exception inside Manifold's deferred
callback handlers.

### Fixed
- Fix issue with Manifold thread-binding interaction.
  [#11](https://github.com/amperity/ken/issues/11)
  [PR#12](https://github.com/amperity/ken/pull/12)

### Changed
- Improve compatibility when used with custom `CompletableFuture`
  implementations.


## [2.0.47] - 2025-03-18

This major release contains a number of significant changes to simplify the
library. The core functionality is unchanged, but several dependencies have
been removed in order to streamline the code. These changes also make the
library more compatible with Graal for native-image generation.

### Changed
- Switch from Leiningen to tools.deps for building the library.

### Removed
- Drop the `alphabase` dependency and inline the hex identifier generation.
- Drop the `manifold` dependency; instead, ken will operate on Java's built-in
  `CompletionStage` interface, which manifold `Deferred` values implement.
- Remove usage of `clojure.spec` in favor of simpler direct value predicates.
  These are compatible with `malli` schema definitions, though there is no
  direct usage of the library.
- Remove support for the legacy trace headers.


## [1.2.0] - 2023-03-20

### Changed
- Update dependency versions.

### Added
- The `ken.tap/flush!` function allows callers to block until all
  previously-sent events have been processed.
  [#5](https://github.com/amperity/ken/issues/5)
  [PR#6](https://github.com/amperity/ken/pull/6)


## [1.1.0] - 2022-11-08

This release has **potentially breaking changes** if you have a dependency on
the specific format of the trace and span identifiers. These changes move ken
more in line with the [OpenTelemetry](https://opentelemetry.io/) standard to
improve interoperability.

### Changed
- Trace identifiers are now 16 bytes of hexadecimal (previously they were 12
  bytes of base32). Span identifiers are now 8 bytes of hex (previously 6 bytes
  of base32).
  [PR#3](https://github.com/amperity/ken/pull/3)

### Added
- A new set of functions in `ken.trace` contain logic for working with the OTel
  `traceparent` header instead of the custom `X-Ken-Trace` header. The previous
  functions are now deprecated.
  [PR#4](https://github.com/amperity/ken/pull/4)


## [1.0.2] - 2022-05-27

### Changed
- Update dependencies to latest stable versions.


## [1.0.1] - 2021-12-27

### Changed
- Update dependencies to latest stable versions.


## [1.0.0] - 2021-06-08

First production release! No changes since `0.3.0`, but this reflects full
internal adoption in Amperity's codebase.


## [0.3.0] - 2021-05-31

### Changed
- Tag macro closures with `^:once` metadata to avoid
  [unexpected object retention](http://clj-me.cgrand.net/2013/09/11/macros-closures-and-unexpected-object-retention/).

### Added
- Add tracing header support to `ken.trace`.


## [0.2.0] - 2021-05-24

Initial open-source project release.

### Changed
- Stripped `amperity` prefix from namespaces and keywords.
- Extend test coverage of code.


[Unreleased]: https://github.com/amperity/ken/compare/2.1.55...HEAD
[2.1.55]: https://github.com/amperity/ken/compare/2.0.50...2.1.55
[2.0.50]: https://github.com/amperity/ken/compare/2.0.47...2.0.50
[2.0.47]: https://github.com/amperity/ken/compare/1.2.0...2.0.47
[1.2.0]: https://github.com/amperity/ken/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/amperity/ken/compare/1.0.2...1.1.0
[1.0.2]: https://github.com/amperity/ken/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/amperity/ken/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/amperity/ken/compare/0.3.0...1.0.0
[0.3.0]: https://github.com/amperity/ken/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/amperity/ken/compare/0.1.0...0.2.0
