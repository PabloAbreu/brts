package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/middle/FindFirstPlaylistCli.java' is part of BRTS.
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
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.brts.lowlevel.parser.MovieObjectsParser;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.middle.scan.FirstPlaylistFinder;
import org.brts.middle.scan.FirstPlaylistFinderConfig;
import org.brts.middle.scan.FirstPlaylistResult;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * CLI for the middle-level "find-first-playlist" command.
 */
public class FindFirstPlaylistCli {

	static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--bdmv-dir", required = true, usage = "Path to the BDMV directory containing index.bdmv and MovieObject.bdmv")
		File bdmvDir;

		@Option(name = "--start-title", usage = "Title number to start from (1-based; default: first-play title)")
		Integer startTitle;

		@Option(name = "--start-object", usage = "Movie object index to start from (0-based; overrides --start-title)")
		Integer startObject;

		@Option(name = "--output", usage = "Output JSON file (default: stdout)")
		File output;

		@Option(name = "--max-depth", usage = "Maximum number of object transitions (default: 100)")
		Integer maxDepth;

		@Option(name = "--min-duration", usage = "Skip PLAY_PL playlists shorter than N seconds (requires --bdmv-dir to contain PLAYLIST/)")
		Integer minDuration;

		@Option(name = "--find-menu", usage = "Also stop at the first menu playlist (requires --bdmv-dir to contain PLAYLIST/)")
		boolean findMenu;

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "find-first-playlist";
		}

		@Override
		public String getDescription() {
			return "Find the first playlist played via HDMV navigation chain";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void execute(Options opts) throws Exception {
			// --- Parse index.bdmv ---
			File indexFile = new File(opts.bdmvDir, "index.bdmv");
			if (!indexFile.isFile()) {
				System.err.println("index.bdmv not found in " + opts.bdmvDir);
				System.exit(1);
			}
			IndexBdmv index;
			try (FileInputStream fis = new FileInputStream(indexFile)) {
				index = new IndexBdmvParser().parse(fis);
			}

			// --- Parse MovieObject.bdmv ---
			File mobjFile = new File(opts.bdmvDir, "MovieObject.bdmv");
			if (!mobjFile.isFile()) {
				System.err.println("MovieObject.bdmv not found in " + opts.bdmvDir);
				System.exit(1);
			}
			MovieObjects movieObjects;
			try (FileInputStream fis = new FileInputStream(mobjFile)) {
				movieObjects = new MovieObjectsParser().parse(fis);
			}

			// --- Build config ---
			FirstPlaylistFinderConfig.FirstPlaylistFinderConfigBuilder cb = FirstPlaylistFinderConfig.builder();
			if (opts.startObject != null)
				cb.startObjectId(opts.startObject);
			else if (opts.startTitle != null)
				cb.startTitleNumber(opts.startTitle);
			if (opts.maxDepth != null)
				cb.maxChainDepth(opts.maxDepth);
			if (opts.minDuration != null)
				cb.minDurationSeconds((long) opts.minDuration);
			cb.stopAtMenu(opts.findMenu);

			// --- Build playlist loader (only when filtering is needed) ---
			FirstPlaylistFinder finder;
			if (opts.minDuration != null || opts.findMenu) {
				File playlistDir = new File(opts.bdmvDir, "PLAYLIST");
				MoviePlaylistParser mplsParser = new MoviePlaylistParser();
				finder = new FirstPlaylistFinder(index, movieObjects, id -> {
					File mplsFile = new File(playlistDir, String.format("%05d.mpls", id));
					try (FileInputStream fis = new FileInputStream(mplsFile)) {
						return Optional.of(mplsParser.parse(fis));
					} catch (IOException e) {
						return Optional.empty();
					}
				});
			} else {
				finder = new FirstPlaylistFinder(index, movieObjects);
			}

			// --- Find first playlist ---
			FirstPlaylistResult result = finder.find(cb.build());

			// --- Output ---
			writeJson(opts.output, result);
			if (opts.output != null) {
				System.out.println("Result → " + opts.output);
			}
			if (result.getDurationSeconds() != null) {
				System.out.printf("Playlist %d duration: %d seconds%n", result.getPlaylistId(),
						result.getDurationSeconds());
			}
		}

	}

}
