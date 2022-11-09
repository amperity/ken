Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

...


## [1.1.0] - 2022-11-08

This release has **potentially breaking changes** if you have a dependency on
the specific format of the trace and span identifiers. These changes move ken
more in line with the [OpenTelemetry](https://opentelemetry.io/) standard to
improve interoperability.

### Changed
- Trace identifiers are now 16 bytes of hexadecimal (previously they were 12
  bytes of base32). Span identifiers are now 8 bytes of hex (previously 6 bytes
  of base32).
  [#3](https://github.com/amperity/ken/pull/3)

### Added
- A new set of functions in `ken.trace` contain logic for working with the OTel
  `traceparent` header instead of the custom `X-Ken-Trace` header. The previous
  functions are now deprecated.
  [#4](https://github.com/amperity/ken/pull/4)


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


[Unreleased]: https://github.com/amperity/ken/compare/1.1.0...HEAD
[1.1.0]: https://github.com/amperity/ken/compare/1.0.2...1.1.0
[1.0.2]: https://github.com/amperity/ken/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/amperity/ken/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/amperity/ken/compare/0.3.0...1.0.0
[0.3.0]: https://github.com/amperity/ken/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/amperity/ken/compare/0.1.0...0.2.0
