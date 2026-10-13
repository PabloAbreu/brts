# Video conversion utility

`org.brts.lowlevel.utils.video.VideoConverter` is a synchronous low-level Java
utility. It reads a **compressed elementary-video file** and produces an Annex B
AVC or HEVC elementary-stream file. It does not extract tracks, accept uncompressed
pixel files, retime audio or mux containers/discs. The CLI exposes it as
`brts low convert-video`.

## CLI

Run `brts low convert-video --help` for command help. All request fields are exposed:

| Argument | Meaning/default |
|---|---|
| `--input FILE`, `--output FILE` | Required compressed elementary-video input and elementary-stream output |
| `--codec CODEC` | `H264_AVC` (default) or `H265_HEVC` (UHD target), case-insensitive |
| `--engine ENGINE` | `FFMPEG`, `NVENCC`, `VCEENCC`, `QSVENCC`, case-insensitive; otherwise configuration, then FFmpeg |
| `--executable PATH` | Selected engine executable path/name on PATH; otherwise engine configuration |
| `--width PIXELS`, `--height PIXELS` | Supply together for aspect-preserving scaling/padding; otherwise source-derived geometry |
| `--frame-rate FPS` | Exact fraction or decimal alias, e.g. `24000/1001` or `23.976`; otherwise source rate |
| `--source-frame-rate FPS` | Source timing override, distinct from target playback rate |
| `--bitrate-kbps KBPS` | Positive bitrate in `1..40000`, mutually exclusive with native quality options |
| `--rate-control MODE` | `VBR`, `CBR`, `CRF`, `CQP`, `ICQ`, `LA_ICQ`, `QVBR`; case-insensitive, `la-icq` also accepted |
| `--engine-option KEY=VALUE` | Repeatable converter-approved native option; no leading dashes or duplicate keys |
| `--overwrite` | Replace an existing regular output file only after successful encoding/validation |
| `--timeout-seconds SECONDS` | Positive whole seconds; omitted means no time limit |

Examples:

```bash
# Bitrate-controlled AVC with the configured engine
brts low convert-video --input source.264 --output converted.264 --bitrate-kbps 20000

# FFmpeg native quality control and an explicit timeout
brts low convert-video --input source.264 --output converted.264 --engine ffmpeg \
	--engine-option crf=20 --engine-option preset=slow --timeout-seconds 1800

# NVEncC native CQP triplet; hardware modes remain best-effort
brts low convert-video --input source.264 --output converted.264 --engine NVEncC \
	--engine-option cqp=18:20:22 --engine-option preset=P4 --engine-option device=0
```

Each `--engine-option` occurrence is split at its **first** `=`. Keys and values
are preserved verbatim, including additional equals signs or colons; quote the
whole `KEY=VALUE` token if it contains shell-significant characters or spaces.
There is no comma-separated option list or shell-command parsing. The parser is
engine-independent, but `VideoConverter` still rejects unknown/protected settings
and invalid values: this is **not** unrestricted native command-line passthrough.
Supply exactly one bitrate or native quality option; the converter validates
engine/rate-control compatibility. There is no implicit bitrate or quality default.

Explicit CLI values override configuration; omitted engine/executable options
retain the existing configuration precedence. The command reports the output path
and `TARGETED`/`BEST_EFFORT` status, with warnings in the converter's logs. Neither
status certifies Blu-ray conformance. Source extraction and audio retiming remain
separate operations. Changing `--frame-rate` changes playback speed, not frame
interpolation/drop/duplication; separately extracted audio must be adjusted.
Hardware engines reject timing overrides/rate changes that they cannot perform
reliably, rather than launching an implicit FFmpeg helper.

## Request and result

Use one mutable `VideoConversionParams` POJO, with setters for:

| Field | Meaning/default |
|---|---|
| `inputPath`, `outputPath` | Required local files; distinct paths |
| `codec` | `StreamCodingType.H264_AVC` by default, or `H265_HEVC` |
| `engine` | Request override; otherwise configuration, then `FFMPEG` |
| `executablePath` | Optional selected engine executable override, never a command string |
| `width`, `height` | Supply together, or retain/adapt source dimensions |
| `frameRate` | Exact `FrameRate`, source default; explicit change changes playback speed |
| `sourceFrameRate` | Optional missing/incorrect source timing override, not target rate |
| `bitrateKbps` | Required positive target bitrate unless a native quality option is supplied |
| `rateControl` | `VBR` by default with bitrate; optional explicit `CBR` or matching quality mode |
| `engineOptions` | Native quality and approved engine-specific settings; names without dashes |
| `overwrite` | False by default; existing output is preserved on failure |
| `timeout` | Optional positive `Duration`; no time limit by default |

Typical AVC request: set paths and `bitrateKbps=20000`, then call
`new VideoConverter().convert(params)`. For FFmpeg quality control, leave bitrate
unset and set `engineOptions` to `Map.of("crf", "20", "preset", "slow")`.
`FrameRate.parse("23.976")` produces `24000/1001`, not a rounded integer.

`prepare(params)` probes the input and returns an immutable `VideoConversionPlan`,
including tokenized command, geometry, rate control, GOP and warnings; it does not
launch an executable or require the encoder to be installed. `convert(params)`
returns `VideoConversionResult` with that plan, final warnings and
`TARGETED`/`BEST_EFFORT` status. Neither status is a conformance certificate.
Do not mutate a request concurrently or run concurrent conversions to one output.

## Configuration

The existing [CLI configuration resource](../brts-cli/src/main/resources/brts.conf)
declares these keys:

| Key | Default |
|---|---|
| `brts.video.converter.engine` | `ffmpeg` |
| `brts.video.converter.ffmpeg.binary` | `/usr/bin/ffmpeg` |
| `brts.video.converter.nvencc.binary` | `NVEncC` |
| `brts.video.converter.vceencc.binary` | `VCEEncC` |
| `brts.video.converter.qsvencc.binary` | `QSVEncC` |

Explicit request values win. Otherwise use `BrtsFileConfig`'s existing file,
environment and system-property precedence. Executable names resolve on PATH;
paths must identify regular executable files. Missing engines/hardware fail;
the converter never switches engines or invokes another executable to preprocess.
Standalone low-level consumers must declare keys in a loaded configuration file:
`BrtsFileConfig` applies environment/system overrides only to already loaded keys.
There are no implicit bitrate or quality defaults.

## Supported progressive target subset

`BluRayVideoConstraints.formats(codec)` exposes the implemented format/rate table.
This first version deliberately supports only square-pixel progressive targets:

| Codec | Dimensions | Frames/second |
|---|---|---|
| AVC, conventional Blu-ray target | 1280×720 | 23.976, 24, 50, 59.94 |
| AVC, conventional Blu-ray target | 1920×1080 | 23.976, 24 |
| HEVC, Ultra HD Blu-ray target | 1920×1080 or 3840×2160 | 23.976, 24, 25, 29.97, 30, 50, 59.94, 60 |

Interlaced sources and interlaced-only target modes (including conventional
1080 at 25/29.97) are rejected, not relabeled or silently deinterlaced. Conventional
Blu-ray does **not** support HEVC; HEVC targets UHD rules, and is not a promise
that existing BRTS disc authoring will produce a certified UHD disc.

Without explicit dimensions, an already supported source is retained. An
unsupported source is padded only when exactly one eligible larger canvas shares
one unchanged dimension: 1920×900 becomes 1920×1080 with 90 black rows above and
below, without scaling. Ambiguous/oversized dimensions fail. Explicit supported
dimensions use aspect-preserving scale/pad, never crop/stretch. Chroma alignment
may assign a two-pixel centering remainder to right/bottom. Anamorphic source
scaling requires an explicit target and the FFmpeg engine.

Missing trustworthy codec timing falls back to 24000/1001 for FFmpeg. A known
unsupported rate is rejected, not treated as missing. Frame rate is a bounded
header/decoder snapshot, not a whole-stream constant-cadence guarantee.
Explicit target frame rate changes speed, retaining each decoded frame once:
`plan.durationRatio()` is source fps / target fps. Audio adjustment is outside scope.
Hardware adapters reject timing overrides, missing timing or changed rates because
their `--fps` switch does not reliably retime compressed input. No helper is used.

## Encoder policy and native options

Automatic, protected settings include a fixed nominal one-second closed GOP
(24/30/60 frames at fractional rates: approximately 1.001 seconds), three B-frames,
three references, profile/level, 4:2:0 output, AUD/repeated headers and buffering
constraints. AVC targets High@4.1, 8-bit, 40 Mbit/s maximum, 30 Mbit CPB.
HEVC targets Main10@5.1/high-tier, 10-bit, a deliberately conservative 40 Mbit/s
maximum and 40 Mbit CPB. These are video-only budgets, not disc/mux budgeting.
CBR uses its target as the peak; CRF/quality and VBR are constrained where the
engine's rate-control mode can honor VBV.

| Engine | Native quality options | Other approved fields | Notes |
|---|---|---|---|
| FFmpeg (`libx264`/`libx265`) | `crf` | `preset`, `threads` | Supports speed changes; libx265 needs 10-bit support |
| NVEncC | `cqp`, `qvbr` | `preset`, `device` | `--bluray` applies to AVC with bitrate/VBV control |
| VCEEncC | `cqp`, `qvbr` (AVC only) | `preset`, `device` | No documented Blu-ray mode; explicit HRD with bitrate control |
| QSVEncC | `cqp`, `icq`, `la-icq` | `quality`, `device` | Blu-ray peak control requires CBR/VBR; rate-control fallback disabled |

Native quality values are not translated between engines. Supply exactly one
quality choice or bitrate; conflicting/missing choices and unsupported modes fail.
CQP accepts a single integer or `I:P:B` triplet, CRF accepts fractional values;
ICQ/CQP and native QVBR ranges are validated (0–51, with ICQ/QVBR requiring a
positive explicit quality). VCE QVBR requires an integer. Lossless/unconstrained
extra command strings are not exposed. Unknown options, aliases, composite encoder
option strings and overrides of converter-owned settings are rejected.

Hardware encoders, unbounded CQP/ICQ modes and experimental x265 UHD support
are reported as **best-effort**. Available hardware may change B-frame/reference
settings. Requests requiring unsupported transformations still fail rather than
producing incorrect geometry/timing. Native GPU resize/pad/range filters must be
present in the selected build. On Linux, QSV uses explicit `qsv` backend and VCE
uses explicit `amf`, not automatic VA-API fallback. Hardware chroma-position
conversion is unsupported; use FFmpeg when known source placement differs.

## Color and HDR

Preserve supported BT.709 SDR, or BT.2020 SDR/HDR10 on HEVC, including available
static mastering-display and content-light metadata. Convert full range to limited
range; normalize chroma placement with actual FFmpeg resampling rather than VUI
relabeling. Unknown SDR colorimetry/range is explicitly warned and assumed
BT.709/limited. Incomplete HDR colorimetry is rejected. Missing static HDR10
metadata is never synthesized. AVC rejects HDR and non-BT.709 sources because
tone mapping/gamut conversion is outside scope. HLG, detected Dolby Vision and
HDR10+ are not preserved and are rejected when detected in the probe prefix.

## Execution, safety and verification

Native probing reads at most 16 MiB and attempts at most 128 packets to obtain the
first decoded frame. HEVC sequence scan flags are checked in the bounded first SPS;
unknown/unsupported multilayer/ambiguous scan information fails. Metadata/scan
changes later in the stream cannot be detected by this snapshot.

Encoding uses tokenized process arguments, concurrent stdout/stderr consumers and
bounded diagnostics. Timeout/interruption terminates the encoder and performs
bounded cleanup, restoring interruption state. Nonzero exit and missing output fail.
A nonempty temporary output is checked for codec, dimensions, progressive scan,
rational timing, square pixels, range, depth, chroma subsampling/placement,
profile/level and preserved color/HDR properties before publication. Previously
existing output and input files are not deleted on encoder failure. Encoder warnings
about ignored/unsupported/downgraded settings are retained in the result.

No automated test encodes video or requires a GPU. Fast unit tests use fake native
probe/process boundaries for policy, commands, configuration, metadata validation,
timeout/interruption and publication cleanup. Full GOP/HRD/VBV validation and
player/disc compliance testing remain optional external/manual work.

Policy references: [x264 Blu-ray authoring](https://www.x264bluray.com/),
[x265 UHD options](https://x265.readthedocs.io/en/master/cli.html),
[NVEncC](https://github.com/rigaya/NVEnc/blob/master/NVEncC_Options.en.md),
[QSVEncC](https://github.com/rigaya/QSVEnc/blob/master/QSVEncC_Options.en.md),
[VCEEncC](https://github.com/rigaya/VCEEnc/blob/master/VCEEncC_Options.en.md).