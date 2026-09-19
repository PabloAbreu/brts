# Roadmap and Non-Goals

Source of truth for the day-to-day backlog is [`TODO.md`](../TODO.md); this
page gives it some structure.

## Planned features

- Support for disc version `0300` (4K):
  - muxers/writers
  - H.265 video
  - menu resolution
  - selectable version everywhere a version currently matters
- Two-step "resize before compose" pipeline to improve image quality in menu
  generation.
- A pure-Java muxer (removing any remaining reliance on external muxing
  tools).

## Documentation effort

- Make a full-featured (several tracks, several audio, subs, menu, popup menus) standalone example
- Document every single feature with examples, several examples if applicable

## Ongoing maintenance

- Finish and test all started features
- Refactor code/CLI options to offer more consistent usage
- Re-organize/rename/tidy the structure of projects and packages (see the
  existing `org.brts.lowlevel.*` / `org.brts.common.m2ts.*` split noted in
  [architecture.md](architecture.md)).

## Explicit non-goals

These are intentionally out of scope and should not be reintroduced without
an explicit decision to change project direction:

- 3D support.
- `CERTIFICATE` folder / content protection metadata support.
- DRM / AACS / any copy-protection scheme — only empty placeholders are
  emitted for structural compatibility.
- Multi-angle or multiple video tracks within a single title.
- Picture-in-picture.
