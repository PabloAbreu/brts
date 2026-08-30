# Architecture

## Module layers

BRTS is split into five Maven modules, each depending only on the ones above
it in this list:

1. **`brts-common`** — shared utilities and cross-module abstractions:
   JSON mapping (`org.brts.common.json.JsonMapperFactory`), config precedence
   (`org.brts.common.utils.BrtsFileConfig`), model classes, MKV/MP4/M2TS
   low-level primitives, exception types.
2. **`brts-low-level`** (depends on `brts-common`) — binary parsers and
   writers for each Blu-ray file type (CLPI, MPLS, index.bdmv,
   MovieObject.bdmv, BDJO, M2TS/TS muxing and demuxing, IGS popup menus, PGS
   subtitles). Low-level components do not choose PID numbers; they do
   exactly what they are told by the descriptor. They must be able to
   round-trip: parsing a file and feeding the result back to the matching
   writer reproduces the same file.
3. **`brts-middle-level`** (depends on `brts-low-level`) — simplified
   authoring APIs on top of low-level building blocks: auto PID allocation,
   playlist assembly across several atomic M2TS streams, menu orchestration
   (see `SetupMenuGenerator`), playlist scanning/preview tools. A middle-level
   component can emit low-level descriptors plus an orchestration script so
   the low-level steps can be replayed by hand.
4. **`brts-high-level`** (depends on `brts-middle-level`) — template-driven
   disc building (e.g. movie with bonus tracks, TV series season) from a
   single high-level descriptor. It resolves globs/patterns over input videos
   and can emit middle-level descriptors plus an orchestration script.
5. **`brts-cli`** (depends on high/middle/low-level modules) — command-line
   entry point (`org.brts.cli.BrtsMain`) exposing every layer as its own set
   of subcommands, so any step can be run standalone, not just through the
   top-level "build a full disc" use case.

```
brts-common → brts-low-level → brts-middle-level → brts-high-level → brts-cli
```

**Rule:** lower layers must never depend on higher layers. Shared logic
should live in the lowest layer that can use it without adding new
dependencies.

## Package layout

- Low-level code currently exists in both `org.brts.lowlevel.*` and
  `org.brts.common.m2ts.*`. This split is existing project state and is not
  migrated wholesale without an explicit request.
- CLI commands are grouped by level under `org.brts.cli.{low,middle,high}`,
  each level backed by a `LevelDispatcher` that registers one
  `FeatureRunner` per command (see `org.brts.cli.FeatureRunner`,
  `org.brts.cli.LevelDispatcher`).

## Core technologies

| Concern               | Library                                              |
|-----------------------|------------------------------------------------------|
| JSON                  | Jackson (`jackson-databind`, `-core`, `-annotations`, `-datatype-jdk8`) |
| CLI parsing           | args4j                                                |
| Logging               | SLF4J + Logback (`@Slf4j`, parameterized logging)     |
| Boilerplate reduction | Lombok                                                |
| Media processing      | ByteDeco FFmpeg artifacts, external tsMuxer           |
| MKV parsing           | `jebml` (fork available at https://github.com/PabloAbreu/jebml )   |
| Testing               | JUnit 5, Mockito, AssertJ                             |

## Descriptor-driven design

Every authoring step is driven by a JSON descriptor at its own level of
abstraction (low/middle/high), matching closely to the underlying binary
format at the low level and getting progressively simpler and more
templatized at higher levels. See [descriptors.md](descriptors.md).
