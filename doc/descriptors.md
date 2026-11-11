# JSON Descriptors

BRTS uses JSON descriptors at every layer instead of large sets of CLI flags.
All descriptor (de)serialization goes through
`org.brts.common.json.JsonMapperFactory.get()` to keep a single consistent
Jackson configuration. Sample descriptors live in [`examples/`](../examples).

## Low-level descriptors

Low-level descriptors mirror the corresponding binary format closely, so that
parsing an existing file and feeding the resulting JSON back to the matching
writer reproduces the same binary file (round-trip guarantee). They describe
things like which source MKV tracks to keep and which target PIDs to use —
low-level components never choose PIDs themselves.

Examples:
- [`examples/00001.clip-descriptor.json`](../examples/00001.clip-descriptor.json) — CLPI (clip info) descriptor
- [`examples/00001.playlist-descriptor.json`](../examples/00001.playlist-descriptor.json) — MPLS (playlist) descriptor
- [`examples/setup-menu-descriptor.json`](../examples/setup-menu-descriptor.json) — setup/settings menu descriptor
- [`examples/title-menu-descriptor.json`](../examples/title-menu-descriptor.json) — title selection menu descriptor
- [`examples/title-menu-thumbnail-descriptor.json`](../examples/title-menu-thumbnail-descriptor.json) — title menu thumbnail composition
- [`examples/batch-run-descriptor.json`](../examples/batch-run-descriptor.json) — ordered list of commands for `batch-run`

### Shrink configuration and preservation descriptors

`low m2ts-shrink --config` accepts [the shrink example](../examples/m2ts-shrink-config.json).
Missing/null `audioPids` and `subtitlePids` retain all tracks in that category; empty arrays
retain none. `audioDowngrades`, `minimumAudioBitrateKbps` and `fixedAudioBitrateKbps` remain
analysis inputs, but any nonempty value is rejected when video execution is requested.
Audio streams selected for execution are copied unchanged.

Shrink planning requires `--output` even with `--defer` or a decision that needs no video
conversion. Before making that decision, a cache miss extracts primary video and configured
retained audio/subtitles, integrating counts of every source transport PID into the same full
extraction pass (after a bounded metadata probe). A valid hit reuses the saved metadata and
extraction without probing or rescanning the original transport. `--defer` postpones encoding,
sampling and muxing, **not extraction**.

Complete immutable caches use `OUTPUT/.brts-shrink/cache/CLIP-ID/<cache-key>/`; default job
workspaces use `OUTPUT/.brts-shrink/CLIP-ID/jobs/<job-key>/`. Deterministic cache keys reflect
source/CLPI identities, stream selection and cache/extractor versions, independently of target
bitrate or engine. Job keys distinguish encoding plans. An explicit `--work-dir` selects a job
workspace inside the canonical output root on the same filesystem. Caches, large work media
and publication staging cannot escape that root through symlinks or cross-filesystem mounts.
Small metadata reports may be stored elsewhere.

For video-required plans, preparation writes `m2ts-descriptor.json`, `cache-reference.json`
and `batch-run.json` to the job workspace. All media, descriptor and destination paths in the
generated batch are absolute. Its `preflightSteps` validate the already complete pinned cache
on every initial run and resume; its two ordinary `steps` convert video at the calculated
bitrate using the same codec, then create the output pair. There is no ordinary extraction
step, and the timing manifest already exists in the cache before execution.

The generated `M2tsDescriptor` enables opt-in preservation using **all four** fields:
`sourceM2ts` and `sourceClpi` (original pair), `timingManifest` (timed extraction manifest),
and positive `maximumOutputBytes` (hard final M2TS byte limit). Its stream entries retain
source PIDs, coding types, languages and video format/frame rate; only the video file points
to the converted elementary stream. Extracted filenames use `pid_%04x` and the standard codec
extension, including `.pgs` containing timed SUP for PGS. The preservation writer requires
tsMuxeR, validates source/timing compatibility and publishes only a validated M2TS/CLPI pair.
The shrink preservation path supports retained AC3/E-AC3, Dolby TrueHD, DTS/DTS-HD HRA/DTS-HD
MA/DTS Express audio and PGS; audio elementary streams are copied unchanged. Strict timing and
layout validation may still reject incompatible source streams.
Ordinary descriptors without preservation fields retain the existing writer behavior.

`sourceM2tsSha256`, `sourceClpiSha256` and timing `sourceSha256` are optional content
fingerprints, not a requirement for cache-backed preservation. Shrink's `--compute-sha256`
opts into SHA-256 verification; default planning/cache validation does not hash source media.
For ordinary extraction, `--source-sha256` supplies an expected M2TS digest, and
`--source-clpi` plus `--source-clpi-sha256` verify the CLPI before extraction writes; the CLPI
path and digest must be supplied together. These fingerprint options are not accepted in
read-only cache-validation mode.

Timing manifests now use `version: 4` and include top-level `sourcePacketCounts`, a JSON
object mapping decimal PID keys to packet counts. Counts cover **all transport PIDs**, not
only extracted streams: selected and removed elementary streams, PSI, null packets,
adaptation-only packets and Dolby Vision companions all contribute. PIDs range from 0 to
8191, counts are nonnegative, and their sum multiplied by 192 must equal `sourceSize`.
Legacy metadata versions remain supported, but repeated timing records must use the new
short field names; no old-name aliases are provided. Existing long-name timing manifests
and shrink caches require re-extraction/replanning (do not rewrite pinned cache artifacts).
Version 1 metadata is not accepted as a new shrink cache.
Optional `sourceSha256`, when supplied, binds all original transport bytes rather
than only extracted PIDs. Absolute unwrapped 90 kHz timing remains unchanged. Video conversion
receives `--source-frame-rate` as an exact rational (for example `24000/1001`), including when
source metadata contains only the parser's Blu-ray frame-rate code.

Timing files are written without pretty printing; other JSON descriptors retain their
normal formatting. Top-level and per-stream metadata keep readable names. Repeated records
use these compact properties (Java accessors and timestamp semantics are unchanged):

| Record | JSON properties |
| --- | --- |
| PES timing | `i`: packetIndex, `o`: outputOffset, `p`: pts90k, `d`: dts90k |
| SUP segment timing | `o`: outputOffset, `t`: type, `s`: size, `p`: pts90k, `d`: dts90k |
| Video entry point | `p`: pts90k, `s`: spn |

Null timestamp properties are omitted, not replaced by synthetic timestamps. Required
primitive properties prevent old long-name records from silently becoming zero-valued
timings. Version 3 introduced the optional per-video `videoScan` metadata; version 4
introduces this compact representation and changes newly generated shrink cache keys.

#### Extraction cache reference

`M2tsDescriptor.extractionCacheReference` is optional and has this shape:

```json
{
  "manifest": "/output/.brts-shrink/cache/00001/cache-key/cache.json",
  "identity": {
    "path": "/output/.brts-shrink/cache/00001/cache-key/cache.json",
    "size": 1234,
    "modified": "2026-11-03T12:34:56.123456789Z",
    "fileKey": null
  }
}
```

`manifest` is the absolute canonical path to the completed cache's `cache.json` marker;
`identity` pins that marker itself, not the original M2TS. `path` is its canonical path,
`size` its exact byte size, `modified` its full-precision file modification time string, and
`fileKey` its provider-specific file identity string, or null where unavailable. Generated
job-local `cache-reference.json` contains this same reference object. The cache manifest
records source/CLPI identities, persisted transport metadata, selected PID coverage and
timing/elementary-stream artifact attributes.

Validation checks the pinned manifest, source/CLPI identities, artifact attributes, timing,
PID coverage and target-root/filesystem constraints. This is metadata-attribute trust,
**not cryptographic integrity**: changes preserving size, timestamp and available file key
may be undetectable without opt-in hashing. Cache-key hashing does not hash media content.
Cache-backed preservation reuses saved source metadata, timing and original elementary
streams instead of re-extracting the source; generated/final media validation remains in
place. Legacy descriptors without `extractionCacheReference` keep source re-extraction
verification, and descriptors without preservation fields keep ordinary mux behavior.

The first preflight is `m2ts-extract --cache-reference JOB/cache-reference.json
--validate-cache-only --preserve-timing --input ORIGINAL-M2TS --output CACHE-DIRECTORY`.
The three cache-mode options are required together; input/output must match the pinned
source/cache directory. Validation reads metadata and timing but performs no writes,
source transport parsing or extraction. `--pids`, `--igs-demux` and fingerprint options
are incompatible with this mode. A stale deferred job fails before encoding, including
on resume; it is never silently rebuilt or replanned.

Caches remain immutable and are retained after successful conversion and after planning
decisions that need no video conversion. Cleanup is manual. Do not delete or alter cache
entries or job workspaces referenced by pending or active jobs. Once affected jobs are no
longer needed, stale entries can be explicitly removed and new plans prepared.

### CLPI regeneration and UHD metadata

`brts low clip-regen` keeps its existing `--input`, `--output`, `--clip-name`, and `--json` arguments. An optional
`--options regen-options.json` accepts a `ClpiRegenOptions` JSON object. The builder auto-selects CLPI version `0300`
if any video is HEVC, has the PMT's 2160p format code (`videoFormat: 8`), or probing detects 4K; otherwise it uses
`0200`. This does not change the default version used by direct `ClipInfoWriter` calls. Positive HEVC/4K detection
cannot be downgraded by a conflicting `videoFormat` override.

The options object supports `probeMode` (`BEST_EFFORT`, the default; `STRICT`; or `DISABLED`) and a `streams` object
whose keys are decimal MPEG-TS PIDs. Explicit non-null fields override detected metadata; otherwise positive probe
evidence is used before existing PMT values/defaults. Explicit `false` and `0` values are overrides. Supported
per-video fields are `videoFormat`, `frameRate`, `aspectRatio`, `ocFlag`, `crFlag`, `dynamicRangeType`, `colorSpace`,
and `hdrPlusFlag`. For example:

```json
{
  "probeMode": "BEST_EFFORT",
  "streams": {
    "4113": {
      "dynamicRangeType": 1,
      "colorSpace": 2,
      "hdrPlusFlag": false,
      "crFlag": false
    }
  }
}
```

The CLPI values are `dynamicRangeType: 0` SDR, `1` HDR10, or `2` Dolby Vision; `colorSpace: 1` BT.709 or `2`
BT.2020 (`0` is reserved). FFmpeg probing is local and bounded to a 16 MiB input prefix and at most 512 packets after
stream analysis, with first-frame metadata per PID. HDR10+ and Dolby Vision flags are set only on positive evidence;
absence in a bounded prefix does not prove absence. `ocFlag` and `crFlag` are not inferred and can be supplied by the
caller. Unresolved values remain unknown in the model, but the current CLPI writer serializes unset metadata bits as
zero/false.

`BEST_EFFORT` warns and continues when probing fails or metadata is unknown. `STRICT` requires a supported dynamic
range and color space for each HEVC stream, either detected or supplied in the options. `DISABLED` skips FFmpeg
probing but still applies PMT-based version selection and caller overrides. Overrides must reference video PIDs in the
input; four-bit fields must be in the range 0–15. This first-frame snapshot is not a complete UHD/HDR validation and
does not detect metadata changes later in the stream.

### Popup menu button layouts

`popupMenu.layout` selects the button arrangement:

- `VERTICAL_LIST` (default): a persistent root column to the left of vertical submenu lists.
- `HORIZONTAL_BOTTOM`: a bottom root row with vertical submenu lists above it.
- `HORIZONTAL_BOTTOM_HORIZONTAL_SUBMENUS`: a bottom root row with one centered horizontal submenu row above it.
  Left/Right wraps within the submenu; Down returns to the matching root control. The submenu is separated from
  the root by 16 pixels. Rows that exceed the available width or do not fit above the root are rejected rather
  than wrapped or resized.

With only one selectable group, either bottom-row layout places its entries and Exit together in a single bottom row.

### Popup menu backgrounds

`popupMenu.backgrounds` adds ordered, non-interactive image layers behind popup menu buttons. Layers in each array are
painted from first to last. `shared` layers appear on every page; `root` layers also appear on audio and subtitle pages
that retain the root controls; `audio` and `subtitles` add role-specific layers. A single-group popup uses `shared` plus
its role-specific layers because it has no cloned root controls.

Raster files use `sourcePath`. SVG backgrounds use the existing synthetic image form with inline `data` or `srcPath`:

In image compositions, relative `sourcePath`, `videoPath`, and `syntheticImage.srcPath` values are resolved against the
composition's base path. Relative paths must remain within that base after normalization; absolute paths are used
unchanged. Non-blank inline SVG `data` takes precedence over `srcPath`. Style-template assets are resolved against the
template directory when the template is loaded.

```json
{
  "popupMenu": {
    "outputClipName": "00800",
    "layout": "HORIZONTAL_BOTTOM",
    "backgrounds": {
      "shared": [
        {
          "source": { "sourcePath": "assets/popup-banner.png" },
          "layout": {
            "mode": "FULL_WIDTH_BOTTOM",
            "height": 220,
            "edgeOffset": 20
          }
        }
      ],
      "audio": [
        {
          "source": {
            "syntheticImage": {
              "type": "svg",
              "data": "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"><rect width=\"100\" height=\"100\" rx=\"8\" fill=\"#111\" fill-opacity=\".8\"/></svg>"
            }
          },
          "layout": {
            "mode": "SELECTABLE_BOUNDS",
            "marginTop": 20,
            "marginRight": 24,
            "marginBottom": 20,
            "marginLeft": 24
          }
        }
      ]
    }
  }
}
```

Layout modes are `ABSOLUTE` (`x`, `y`, `width`, `height`), `SELECTABLE_BOUNDS` (four optional margins),
`FULL_WIDTH_BOTTOM` (`height` and optional `edgeOffset`, the distance from the bottom of the screen), and
`FULL_HEIGHT_LEFT` (`width` and optional `edgeOffset`, the distance from the left of the screen). Sources are
stretched to the calculated rectangle without preserving aspect ratio. Rectangles must stay within the configured
screen dimensions. Relative raster and SVG paths resolve from the process working directory. Video and animated SVG
backgrounds are not supported in popup IGS.

### Batch-run descriptor

`brts low batch-run --descriptor batch.json` runs an ordered list of BRTS commands in-process
([`examples/batch-run-descriptor.json`](../examples/batch-run-descriptor.json)). Each entry of `steps` is either an
invocation (`level`, `command`, ordered `arguments` of `{ "flag", "value" }` — omit `value` for boolean flags) or a
`comment` (logged) / `message` (printed) step. Every level/command is checked before the first step runs. Relative
paths in arguments resolve from the process working directory.

An optional top-level `preflightSteps` array uses the **same `BatchStep` schema and validation**
as `steps`, including invocation, comment and message forms. It defaults to empty when omitted
(null is also treated as empty), so existing descriptors need no changes. All commands in both
arrays are checked before execution. Preflight runs in order before ordinary steps on **every**
invocation, both initially and with `--resume`; it is never skipped using ordinary-step progress.
A failing preflight stops execution without changing saved progress. Shrink batches use it for
read-only cache validation before `video-convert` and `m2ts-create`.

Execution stops at the first failing step. Progress is kept in `<java.io.tmpdir>/brts-batch-run/`; after fixing the
problem (in referenced files, or in the failed and later steps of the batch), re-run with `--resume` to continue from
the failed ordinary step, after rerunning all preflight steps. Editing already completed steps
or changing the preflight definitions invalidates saved resume progress. Legacy progress without
preflight definitions remains compatible with descriptors having no preflight. A run without
`--resume` always runs preflight and then starts from the first ordinary step.

The middle-level orchestrator writes such a descriptor as `orchestrate.json` with `--output-type JSON`.

## Middle-level descriptors

Middle-level descriptors point to source MKV files and simplified authoring
intent (e.g. "this MKV becomes title N"); the middle-level orchestrator
auto-parses the MKV, auto-allocates PIDs, and generates the low-level
descriptors and orchestration script needed to produce the final files.

## High-level descriptors

High-level descriptors describe an entire disc from a template
(`templateType`), with globbing over input files and disc-wide metadata. The
high-level orchestrator resolves this into middle-level descriptors plus an
orchestration script.

`templateType: "MOVIE"` — [`examples/movie-disc.json`](../examples/movie-disc.json):

```json
{
  "templateType": "MOVIE",
  "discTitle": "My Movie",
  "outputDirectory": "/tmp/my-movie-disc",
  "mainFeature": {
    "sourceMkv": "/path/to/movie.mkv",
    "audioLanguages": ["eng", "fra"],
    "subtitleLanguages": ["eng"]
  },
  "bonusTracks": [
    {
      "sourceMkv": "/path/to/making-of.mkv",
      "label": "Making Of",
      "audioLanguages": ["eng"]
    }
  ]
}
```

`templateType: "TV_SERIES"` — [`examples/tv-series-disc.json`](../examples/tv-series-disc.json):

```json
{
  "templateType": "TV_SERIES",
  "discTitle": "My Series — Season 1",
  "outputDirectory": "/tmp/my-series-s01",
  "seriesName": "My Series",
  "seasonNumber": 1,
  "episodesGlob": "/path/to/season1/*.mkv",
  "audioLanguages": ["eng"],
  "subtitleLanguages": ["eng", "fra"],
  "generateEpisodeMenu": true
}
```

Each high-level template produces a full disc under `outputDirectory`,
including auto-generated navigation and menus where applicable (e.g. an
episode menu for TV series).

### Style templates

The optional `styleTemplate` field selects a style template that provides
menu text styles and default media assets:

- `"brts:<name>"` — an installed template, looked up as
  `<root>/styles/<name>/`. The root is `brts.assets.dir` from `brts.conf` when
  set, otherwise `~/.brts/assets` then `/usr/share/brts/assets`.
- any other value — a path to a template folder.

A template folder contains a `style-template.json` manifest; every asset path
in it is relative to the folder:

| Field | Applied to |
|-------|-----------|
| `style` | disc-wide `TextStyle` (middle-level `style`) |
| `popupMenu` | popup `layout`, `screenWidth`, `screenHeight`, `style`, `backgrounds` |
| `titleMenu.style`, `layoutType`, `boundingBox` | title menu configuration |
| `titleMenu.backgroundSource` | title menu screen background (video, image or composition, optional `audioPath`) |

The template background is used when the descriptor provides none
(`menuBackgroundVideoPath` wins for TV series). MOVIE discs get a title menu
only when a style template with a title menu background is set and there is
more than one title.

The repository ships `assets/styles/sci-fi` (cyan HUD panels on a dark navy
backdrop, no background audio) and `assets/styles/sober-dark` (black, square
grey frames, white text and a selected-only yellow underline). Install with
`mkdir -p ~/.brts/assets/styles && cp -r assets/styles/* ~/.brts/assets/styles/`,
or point `brts.assets.dir` (e.g. `-Dbrts.assets.dir=$PWD/assets`) at the
repository `assets` folder during development. Example:
[`examples/tv-series-disc-sci-fi.json`](../examples/tv-series-disc-sci-fi.json).

Button text styles may optionally specify `stateOverlays` with `normal`,
`selected` and/or `activated` image references. Each state accepts a static
`sourcePath` or a `syntheticImage` with `type: "svg"` and either inline `data`
or a file-backed `srcPath`. Missing states leave their button images unchanged;
item styles override shared styles state by state. An SVG is rendered as a
FreeMarker template with the final button's `width`, `height`, `textX`,
`textY` (text baseline), `textWidth`, and `textHeight`. The label bounds refer
to the last visible line when text wraps; a button without a label has
`textWidth = 0`. Overlays must render at exactly the button's dimensions.
They are composited over the text/icon before encoding the button states.
Popup background SVG templates also receive `width` and `height`, but title
menu composition backgrounds do not receive template variables.

## Descriptor validation

Descriptors loaded through a `--descriptor` option are validated with standard
Jakarta Bean Validation annotations before the feature starts processing, so
problems such as a missing source MKV or an unwritable output folder are
reported up-front instead of failing mid-build. Every violation is listed at
once with its JSON path:

```
simple-build: Invalid arguments for 'simple-build' (2 problem(s)):
  - descriptor.discName: must not be blank (actual: )
  - descriptor.sourceMkv: file does not exist (actual: /missing/movie.mkv)
```

Constraints are declared directly on the descriptor fields using
`jakarta.validation.constraints.*` plus the BRTS filesystem constraints in
`org.brts.common.validation` (`@ExistingFile`, `@ExistingDirectory`,
`@WritableDirectory`). Nested descriptors are validated when the holding field
or collection element is annotated with `@Valid`.
