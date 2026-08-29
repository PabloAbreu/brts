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
