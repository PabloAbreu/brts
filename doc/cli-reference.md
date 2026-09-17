# CLI Reference

The CLI entry point is `org.brts.cli.BrtsMain`, packaged as a fat jar by
`brts-cli` (`maven-assembly-plugin`) or reachable directly via
`brts_debug_launch.sh` when developping:

```bash
java -jar brts-cli.jar <level> <command> [options]
# or
./brts_debug_launch.sh <level> <command> [options]
```

Run with no arguments, or `<level>` alone, to print the list of commands for
that level. Run `<level> <command> --help` for the option list of a specific
command (options are parsed with args4j).

## Startup banner

An ASCII banner with the version, level and arguments is printed on **stderr**
at startup, so it never interferes with commands that write JSON to stdout.
Disable it by setting `brts.cli.banner=false` through any source supported by
`BrtsFileConfig`:

```bash
java -Dbrts.cli.banner=false -jar brts-cli.jar low clip-parse --input 00001.clpi
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
| `m2ts-extract` | Demux elementary streams from an M2TS file |
| `m2ts-create` | Mux elementary streams into a new M2TS file with matching CLPI |
| `m2ts-igs-mux` | Mux an IGS stream into a new M2TS file with matching CLPI |
| `m2ts-dump` | Dump M2TS packet information for debugging |
| `clip-parse` | Parse a `.clpi` binary file to JSON |
| `clip-write` | Generate a `.clpi` binary from a JSON descriptor |
| `clpi-regen` | Regenerate a CLPI file from an M2TS stream |
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
| `igs-demux` | Demux a raw IGS elementary stream into resources |
| `igs-mux` | Mux demuxed IGS resources back into an IGS stream |
| `rle-to-png` | Convert IGS RLE bitmaps to PNG images |
| `pgs-create` | Generate a PGS subtitle stream from SRT/SSA/ASS |
| `render-template` | Render an output file from a template and a data model (e.g. JSON) |

## `mid` — middle-level disc authoring operations

| Command | Description |
|---|---|
| `build` | Build a Blu-ray disc from a middle-level descriptor |
| `simple-build` | Build a single-title Blu-ray disc with no top menu, from a simplified descriptor |
| `scan-playlists` | Scan Blu-ray playlists and auto-detect content type |
| `find-first-playlist` | Find the first playlist played via HDMV navigation chain |
| `create-setup-menu` | Generate a Blu-ray setup/settings menu M2TS |
| `ds-preview` | Interactive Swing preview for IGS menus in M2TS |

## `high` — high-level template-based disc building

| Command | Description |
|---|---|
| `build` | Build a Blu-ray disc from a high-level template descriptor |

## Examples

```bash
# Parse an existing CLPI file to inspect it
brts low clip-parse --input BDMV/CLIPINF/00001.clpi

# Round-trip: regenerate the same CLPI from parsed JSON
brts low clip-write --input 00001.clpi.json --output 00001.clpi

# Build a full disc from a high-level template descriptor
brts high build --descriptor examples/movie-disc.json --output /tmp/out

# Scan an existing disc's playlists to identify content type
brts mid scan-playlists --input /path/to/BDMV

# Build a single-title disc with no top menu from a simplified descriptor
brts mid simple-build --descriptor examples/simple-build-descriptor.json --output /tmp/out
```

See [descriptors.md](descriptors.md) for the JSON formats referenced above.
