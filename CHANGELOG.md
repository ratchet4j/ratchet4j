# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) once 1.0 is reached (0.x releases may change the CLI
and the baseline schema).

## [Unreleased]

## [0.1.0] - 2026-11-01

First public release (alpha).

### Added
- Core engine: common violation model, content-based fingerprints, four-tier one-to-one matcher,
  baseline file with deterministic output, ratchet (baseline can only shrink), strict-rule policy.
- Readers for Checkstyle XML, PMD XML, SpotBugs XML and SARIF 2.1.0, with format auto-detection.
- CLI commands `baseline`, `check`, `update`, `report` and `version`.
- Zero runtime dependencies.
- CI: build and tests on Java 17 and 21, Windows and macOS, a self-check (ratchet4j guards its own
  code), link and workflow checks, and a release workflow with a two-month release cycle.
