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

### Popup menu backgrounds

`popupMenu.backgrounds` adds ordered, non-interactive image layers behind popup menu buttons. Layers in each array are
painted from first to last. `shared` layers appear on every page; `root` layers also appear on audio and subtitle pages
that retain the root controls; `audio` and `subtitles` add role-specific layers. A single-group popup uses `shared` plus
its role-specific layers because it has no cloned root controls.

Raster files use `sourcePath`. SVG backgrounds use the existing synthetic image form with inline `data` or `srcPath`:

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
