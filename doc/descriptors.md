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

Preparation writes `m2ts-descriptor.json` and `batch-run.json` to the workspace. All media,
descriptor and destination paths in the generated batch are absolute. The three ordered
invocations extract selected PIDs with timing, convert video at the calculated bitrate using
the same codec, then create the output pair. The manifest path can refer to a file that does
not yet exist: extraction creates `extracted/timing.json` during execution.

The generated `M2tsDescriptor` enables opt-in preservation using **all four** fields:
`sourceM2ts` and `sourceClpi` (original pair), `timingManifest` (timed extraction manifest),
and positive `maximumOutputBytes` (hard final M2TS byte limit). Its stream entries retain
source PIDs, coding types, languages and video format/frame rate; only the video file points
to the converted elementary stream. Extracted filenames use `pid_%04x` and the standard codec
extension, including `.pgs` containing timed SUP for PGS. The preservation writer requires
tsMuxeR, validates source/timing compatibility and publishes only a validated M2TS/CLPI pair.
Ordinary descriptors without preservation fields retain the existing writer behavior.

Shrink preparation also records `sourceM2tsSha256` and `sourceClpiSha256` (streamed SHA-256
of the original pair). These fields are optional for legacy descriptors. The first batch
step passes `--source-sha256`, `--source-clpi` and `--source-clpi-sha256` to `m2ts-extract`:
both files are verified before extraction creates or overwrites output, so a changed CLPI
also stops the batch before video encoding. The CLPI path and its digest must be supplied
together. Ordinary extraction without these options is unchanged.

Every successful `--preserve-timing` extraction records `sourceSha256` in `timing.json`,
computed from all source packets during extraction (including unselected PIDs), alongside
`sourceSize`. This binds the extracted media/timing to the source bytes, not just its path
or length. Older manifests lacking the digest remain readable. Video conversion receives
`--source-frame-rate` as an exact rational (for example `24000/1001`), including when source
metadata contains only the parser's Blu-ray frame-rate code.

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

Execution stops at the first failing step. Progress is kept in `<java.io.tmpdir>/brts-batch-run/`; after fixing the
problem (in referenced files, or in the failed and later steps of the batch), re-run with `--resume` to continue from
the failed step. Editing already completed steps invalidates the saved progress. A run without `--resume` always
starts from the first step.

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
