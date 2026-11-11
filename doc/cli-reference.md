# CLI Reference

The CLI entry point is `org.brts.cli.BrtsMain`, packaged as a fat jar by
`brts-cli` (`maven-assembly-plugin`) or reachable directly via
`brts_debug_launch.sh` when developping:

```bash
java -jar brts-cli-<classifier>.jar <level> <command> [options]
# or
./brts_debug_launch.sh <level> <command> [options]
```

Run with no arguments, or `<level>` alone, to print the list of commands for
that level. Run `<level> <command> --help` for the option list of a specific
command (options are parsed with args4j).

## M2TS shrinking

`low m2ts-shrink` requires `--disc-root`, a five-digit `--clip-id`, positive `--target-bytes`
and `--output`, a separate destination disc root, for **all planning**. Filtering is supplied
with `--config`. The planner JSON is printed to stdout or written with `--report`;
execution details (bitrate, batch, workspace and output paths) follow on stderr.
Only `VIDEO_CONVERSION_REQUIRED` executes video conversion. Other decisions remain
analysis-only and do not publish filtered media, but still require output storage for planning.

Before deciding whether video conversion is needed, planning extracts the primary video and
selected retained audio/subtitles with timing, counting **every transport PID in that same
full source pass**. This includes removed tracks, PSI, null packets and any Dolby Vision
companion PID, even though those streams are not necessarily extracted. A bounded metadata
probe may precede extraction; “one pass” does not mean a single source open. A valid cache hit
reuses persisted metadata, timing, elementary streams and counts without another source probe
or extraction. Strict extraction errors can therefore fail planning.

Complete immutable extraction caches live at
`OUTPUT/.brts-shrink/cache/CLIP-ID/<cache-key>/`. Default job workspaces live separately at
`OUTPUT/.brts-shrink/CLIP-ID/jobs/<job-key>/`. Keys are deterministic: cache reuse depends on
source/CLPI metadata identity, selected streams and cache/extractor versions, not target bitrate
or engine; job workspaces distinguish encoding plans. `--work-dir` overrides the job workspace
only and must remain inside the canonical output root and on its filesystem. Symlink escapes,
source aliases and cross-filesystem mounts are rejected. All large media, caches, job outputs
and publication staging stay under that root on the same filesystem, not in the source tree
or OS temporary directory. A small `--report` may be written elsewhere.

Immediate execution is the default. `--defer` prepares the cache and mux/batch descriptors,
including the selected extraction/count pass on a cache miss, but performs no video encoding,
sampling or muxing. Run the generated descriptor with
`low batch-run --descriptor ABSOLUTE-BATCH-PATH`; add `--resume` after a failed batch to keep
its completed ordinary-step prefix. Every initial run and resume first validates the pinned
cache in preflight, then runs the remaining ordinary steps: `video-convert`, followed by
`m2ts-create`. Preflight is never skipped as already completed. Video conversion uses
`--overwrite` for safe step retries. Preparation refuses existing workspace descriptors:
execute/resume their batch, or choose a new workspace.

Cache validation trusts canonical paths, exact sizes, full-precision modification times and
file keys where available, plus metadata/timing consistency; it is **not cryptographic**.
Same-size changes that preserve those attributes may go undetected. `--compute-sha256` opts
into content fingerprinting; it is not the default cache-validation mode. A stale deferred or
resumed job fails before encoding rather than silently re-extracting or changing its plan;
replan against valid inputs. Complete caches remain after success and after decisions requiring
no video conversion. Cleanup is manual: do not delete caches or workspaces needed by pending
or active jobs, and do not modify cached elementary streams.

Video conversion publishes only `BDMV/STREAM/CLIP-ID.m2ts` and
`BDMV/CLIPINF/CLIP-ID.clpi`. Existing destination files and source-root aliases (including
symlinks) are rejected. Source files, playlists and the rest of the disc are not copied or modified.

`--engine` supports FFMPEG, NVENCC, VCEENCC and QSVENCC; the configured/default engine is used
when omitted. The encoder and tsMuxeR are required only when executing, not while generating
the batch. No Java-mux fallback is allowed for this preservation workflow.

Supported input is one primary AVC/HEVC video (PID `0x1011`), retained AC3/E-AC3, Dolby TrueHD,
DTS (including DTS-HD HRA, DTS-HD MA and DTS Express) audio, and PGS subtitles. Retained audio is
copied unchanged. Audio transformations and bitrate hints, interactive/unknown streams,
secondary video and unsupported retained codecs are rejected. No codec, frame rate, geometry or
playback speed change is requested. PGS is extracted as timed SUP data while retaining its usual
`.pgs` filename extension. Preservation validation may reject additional incompatible source layouts.

The video rate is rounded down to 1–40000 kbps after planner safety, retained nonvideo packet
bytes, a further 2% mux reserve and 192/184 transport expansion. This is a conservative budget,
not a size guarantee: oversize output fails before publication, with no automatic bitrate retry.

### Read-only extraction cache preflight

The generated preflight invokes `low m2ts-extract --cache-reference JOB/cache-reference.json
--validate-cache-only --preserve-timing --input ORIGINAL-M2TS --output CACHE-DIRECTORY`
(with absolute paths in generated descriptors). All three cache-mode flags are required
together. `--input` must identify the cached original source and `--output` the existing cache
directory. This mode reads cache metadata/timing and checks source, CLPI and artifact attributes
and workspace placement; it performs **no filesystem writes, source transport parsing or
extraction**. It rejects `--pids`, `--igs-demux` and source fingerprint options. Ordinary
`m2ts-extract` behavior is unchanged. See [cache reference schema](descriptors.md#extraction-cache-reference)
and [batch preflight semantics](descriptors.md#batch-run-descriptor).

## Startup banner

An ASCII banner with the version, level and arguments is printed on **stderr**
at startup, so it never interferes with commands that write JSON to stdout.
Disable it by setting `brts.cli.banner=false` through any source supported by
`BrtsFileConfig`:

```bash
java -Dbrts.cli.banner=false -jar brts-cli-<classifier>.jar low clip-parse --input 00001.clpi
BRTS_CLI_BANNER=false ./brts_debug_launch.sh low clip-parse --input 00001.clpi
```

## `low` — low-level binary file operations

| Command | Description |
|---|---|
| `brts-info` | Display information about the BRTS toolkit, such as version and config properties. |
| `mkv-info` | Inspect an MKV file and emit track metadata as JSON |
| `mkv-extract` | Demux elementary streams from an MKV file |
| `mp4-info` | Inspect an MP4/MOV file and emit track metadata as JSON |
| `m2ts-info` | Inspect stream and timing metadata of an M2TS file |
| `m2ts-probe` | Probe M2TS transport and decoded video metadata |
| `m2ts-extract` | Demux elementary streams from an M2TS file |
| `m2ts-create` | Mux elementary streams into a new M2TS file with matching CLPI |
| `m2ts-shrink` | Analyze stream selection; execute or defer video-required shrinking into a separate disc root |
| `m2ts-igs-mux` | Mux an IGS stream into a new M2TS file with matching CLPI |
| `m2ts-dump` | Dump M2TS packet information for debugging |
| `clip-parse` | Parse a `.clpi` binary file to JSON |
| `clip-write` | Generate a `.clpi` binary from a JSON descriptor |
| `clip-regen` | Regenerate a CLPI file from an M2TS stream |
| `playlist-parse` | Parse a `.mpls` binary file to JSON |
| `playlist-write` | Generate a `.mpls` binary from a JSON descriptor |
| `find-playlist` | Find playlists referencing a given clip name |
| `playlist-to-mkv` | Extract a Blu-ray playlist to an MKV container |
| `mkv-to-playlist` | Convert an MKV file to Blu-ray M2TS/CLPI/MPLS |
| `index-parse` | Parse a binary `index.bdmv` file to JSON |
| `index-write` | Generate a binary `index.bdmv` from a JSON model |
| `mobj-parse` | Parse a binary `MovieObject.bdmv` file to JSON |
| `mobj-write` | Generate a binary `MovieObject.bdmv` from a JSON model |
| `bdjo-parse` | Parse a binary `.bdjo` file to JSON |
| `bdjo-write` | Generate a binary `.bdjo` from a JSON model |
| `nav-simul` | Simulate HDMV navigation commands with virtual registers |
| `disc-create` | Create a full disc from a descriptor and video files |
| `create-title-menu` | Generate a Blu-ray title selection menu (MPLS + M2TS/CLPI) |
| `video-gen` | Generate a composited M2TS video from an ImagesComposition descriptor |
| `convert-video` | Convert compressed elementary video to AVC/HEVC using FFmpeg or hardware encoders |
| `igs-demux` | Demux a raw IGS elementary stream into resources |
| `igs-mux` | Mux demuxed IGS resources back into an IGS stream |
| `rle-to-png` | Convert IGS RLE bitmaps to PNG images |
| `pgs-create` | Generate a PGS subtitle stream from SRT/SSA/ASS |
| `render-template` | Render an output file from a template and a data model (e.g. JSON) |
| `batch-run` | Run an ordered list of BRTS commands from a JSON descriptor (fail-fast, resumable with `--resume`) |

`convert-video` requires `--input`, `--output` and either `--bitrate-kbps` or a native quality option.
Pass approved encoder options generically by repeating `--engine-option KEY=VALUE`, for example
`--engine-option crf=20 --engine-option preset=slow`. Keys have no leading dashes and must be unique.
`--timeout-seconds` sets a positive whole-second time limit; `--overwrite` permits output replacement.
See [video conversion](video-conversion.md#cli) for all arguments, engine restrictions and timing caveats.

`m2ts-probe --input FILE [--output FILE]` emits the `m2ts-info` transport/PMT data in an `info` field and
per-video-PID decoded snapshots in `videoMetadataByPid`. Snapshots may include Blu-ray `videoFormat`, `fourK`,
`dynamicRangeType`, `colorSpace`, and positive HDR10+ evidence (`hdrPlusFlag`). This is a bounded prefix probe, not a
full-stream validation: absent HDR evidence is unknown, not proof of absence. A probe failure fails the command;
files with no video streams return an empty `videoMetadataByPid` map. The backend defaults to `ffprobe` and falls back
to the bounded FFmpeg library probe if the executable is unavailable; `brts.clpi.probe.engine=ffmpeg` selects the
library explicitly.

## `mid` — middle-level disc authoring operations

| Command | Description |
|---|---|
| `build` | Build a Blu-ray disc from a middle-level descriptor |
| `simple-build` | Build a single-title Blu-ray disc with no top menu, from a simplified descriptor |
| `scan-playlists` | Scan Blu-ray playlists and auto-detect content type |
| `find-first-playlist` | Find the first playlist played via HDMV navigation chain |
| `create-setup-menu` | Generate a Blu-ray setup/settings menu M2TS |
| `ds-preview` | Interactive Swing preview for IGS menus in M2TS |

`build` and `simple-build` accept `--output-type`: `IMMEDIATE` runs the low-level steps directly, `BASH` writes
`orchestrate.sh`, and `JSON` writes `orchestrate.json` to be run with `brts low batch-run`.

## `high` — high-level template-based disc building

| Command | Description |
|---|---|
| `build` | Build a Blu-ray disc from a high-level template descriptor |
| `title-menu-simulate` | Render a single JPEG preview frame of a high-level title menu |

`title-menu-simulate` requires `--descriptor` and `--output`. It previews the background at
`--time-seconds` (default `0`), with `--selected-title` (default `1`) shown in
`--button-state` (`normal`, `selected`, or `activated`; default `selected`). Relative media paths
are resolved from the current directory unless `--base-dir` is supplied. Previewing does not
generate Blu-ray authoring files, and it allows a single-title menu even when a normal build would
omit one.

## Examples

```bash
# Parse an existing CLPI file to inspect it
brts low clip-parse --input BDMV/CLIPINF/00001.clpi

# Round-trip: regenerate the same CLPI from parsed JSON
brts low clip-write --input 00001.clpi.json --output 00001.clpi

# Convert compressed elementary video with FFmpeg quality control
brts low convert-video --input source.264 --output converted.264 --engine ffmpeg \
	--engine-option crf=20 --engine-option preset=slow

# Build a full disc from a high-level template descriptor
brts high build --descriptor examples/movie-disc.json --output /tmp/out

# Preview the title menu defined by a high-level descriptor and style template
brts high title-menu-simulate --descriptor examples/tv-series-disc-sci-fi.json --output /tmp/title-menu.jpg

# Scan an existing disc's playlists to identify content type
brts mid scan-playlists --input /path/to/BDMV

# Build a single-title disc with no top menu from a simplified descriptor
brts mid simple-build --descriptor examples/simple-build-descriptor.json --output /tmp/out

# Generate the low-level steps as a batch descriptor, run them, and resume after a failure
brts mid simple-build --descriptor examples/simple-build-descriptor.json --output /tmp/out --output-type JSON
brts low batch-run --descriptor /tmp/out/orchestrate.json
brts low batch-run --descriptor /tmp/out/orchestrate.json --resume
```

See [descriptors.md](descriptors.md) for the JSON formats referenced above.
