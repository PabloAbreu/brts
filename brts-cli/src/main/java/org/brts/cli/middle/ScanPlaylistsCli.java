package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/middle/ScanPlaylistsCli.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

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

	static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--playlist-dir", required = true, usage = "Path to the BDMV/PLAYLIST directory containing .mpls files")
		File playlistDir;

		@Option(name = "--output", usage = "Output JSON file (default: stdout)")
		File output;

		@Option(name = "--type", usage = "Force disc content type: MOVIE or TV_SERIES (default: auto-detect)")
		String forcedType;

		@Option(name = "--movie-min", usage = "Minimum duration in minutes for a playlist to be a movie (default: 80)")
		Double movieMinMinutes;

		@Option(name = "--alt-cut-ratio", usage = "Min ratio vs longest playlist to qualify as alternate cut (default: 0.85)")
		Double movieAlternateCutRatio;

		@Option(name = "--episode-min", usage = "Minimum duration in minutes for a playlist to be an episode (default: 22)")
		Double episodeMinMinutes;

		@Option(name = "--episode-max-ratio", usage = "Max duration ratio between longest and shortest episode candidate (default: 2.0)")
		Double episodeMaxDurationRatio;

		@Option(name = "--episode-min-count", usage = "Minimum number of similar-duration playlists for TV-series detection (default: 2)")
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
