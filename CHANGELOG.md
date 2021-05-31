Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]


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


[Unreleased]: https://github.com/amperity/ken/compare/0.3.0...HEAD
[0.3.0]: https://github.com/amperity/ken/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/amperity/ken/compare/0.1.0...0.2.0
