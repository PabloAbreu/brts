# Changelog

All notable changes to BRTS after version `0.0.2` are documented here. Related work-in-progress commits and follow-up fixes are grouped by feature area.

This changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.3] - 2026-11-27

### Added
- Disc-wide analysis, diagnostics, and human-readable reports.
- M2TS and whole-disc shrinking workflows, including UHD/HEVC and audio support.
- Video probing and conversion, configurable muxing, and playlist handling.
- Expanded menu authoring: adaptive and popup layouts, full-disc templates, PGS preview, and Jungle/Sober-Dark themes.
- SVG templating, batch execution, Bash completion, and additional `brts-info` capabilities.
- Platform-specific build and packaging support.

### Changed
- Refactored CLI dispatch and improved feature sorting and logging.
- Improved artifact naming, path handling, Javadocs, and README documentation.

### Fixed
- Improved parsing and handling of PMT, playlist, BDJO, and MovieObject data.
- Fixed issues involving frame rates, paths, streams, and generated files.
- Consolidated formatting and license-plugin fixes, preview refactoring, and shrink workflow follow-ups.
