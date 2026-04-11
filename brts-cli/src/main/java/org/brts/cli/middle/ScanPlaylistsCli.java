package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.middle.scan.DiscContentType;
import org.brts.middle.scan.PlaylistScanConfig;
import org.brts.middle.scan.PlaylistScanResult;
import org.brts.middle.scan.PlaylistScanner;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;

/**
 * CLI for the middle-level "scan-playlists" command.
 */
public class ScanPlaylistsCli {

	static class Options {

		@Option(name = "--playlist-dir", required = true,
				usage = "Path to the BDMV/PLAYLIST directory containing .mpls files")
		File playlistDir;

		@Option(name = "--output", usage = "Output JSON file (default: stdout)")
		File output;

		@Option(name = "--type", usage = "Force disc content type: MOVIE or TV_SERIES (default: auto-detect)")
		String forcedType;

		@Option(name = "--movie-min", usage = "Minimum duration in minutes for a playlist to be a movie (default: 80)")
		Double movieMinMinutes;

		@Option(name = "--alt-cut-ratio",
				usage = "Min ratio vs longest playlist to qualify as alternate cut (default: 0.85)")
		Double movieAlternateCutRatio;

		@Option(name = "--episode-min",
				usage = "Minimum duration in minutes for a playlist to be an episode (default: 22)")
		Double episodeMinMinutes;

		@Option(name = "--episode-max-ratio",
				usage = "Max duration ratio between longest and shortest episode candidate (default: 2.0)")
		Double episodeMaxDurationRatio;

		@Option(name = "--episode-min-count",
				usage = "Minimum number of similar-duration playlists for TV-series detection (default: 2)")
		Integer episodeMinCount;

		@Option(name = "--include-menus", usage = "Include menu playlists in results (default: excluded)")
		boolean includeMenus = false;

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "scan-playlists";
		}

		@Override
		public String getDescription() {
			return "Scan Blu-ray playlists and auto-detect content type";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void execute(Options opts) throws Exception {
			PlaylistScanConfig.PlaylistScanConfigBuilder cb = PlaylistScanConfig.builder();

			if (opts.forcedType != null) {
				cb.forcedType(DiscContentType.valueOf(opts.forcedType.toUpperCase()));
			}
			if (opts.movieMinMinutes != null)
				cb.movieMinMinutes(opts.movieMinMinutes);
			if (opts.movieAlternateCutRatio != null)
				cb.movieAlternateCutRatio(opts.movieAlternateCutRatio);
			if (opts.episodeMinMinutes != null)
				cb.episodeMinMinutes(opts.episodeMinMinutes);
			if (opts.episodeMaxDurationRatio != null)
				cb.episodeMaxDurationRatio(opts.episodeMaxDurationRatio);
			if (opts.episodeMinCount != null)
				cb.episodeMinCount(opts.episodeMinCount);
			if (opts.includeMenus)
				cb.excludeMenus(false);

			PlaylistScanConfig config = cb.build();

			PlaylistScanner scanner = new PlaylistScanner();
			Path playlistDirPath = opts.playlistDir.toPath();
			PlaylistScanResult result = scanner.scan(playlistDirPath, config);

			writeJson(opts.output, result);
			if (opts.output != null) {
				System.out.println("Scan result → " + opts.output);
			}
		}

	}

}
