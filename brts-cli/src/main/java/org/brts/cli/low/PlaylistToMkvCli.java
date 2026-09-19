package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/PlaylistToMkvCli.java' is part of BRTS.
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
import org.brts.lowlevel.m2ts.PlaylistToMkvExtractor;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI for extracting a Blu-ray playlist directly to an MKV container.
 */
public class PlaylistToMkvCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--playlist", required = true, usage = "Path to the .mpls playlist file")
		File playlist;

		@Option(name = "--stream-dir", required = true, usage = "Directory containing M2TS files (e.g. BDMV/STREAM)")
		File streamDir;

		@Option(name = "--output", required = true, usage = "Output MKV file path")
		File output;

		@Option(name = "--audio-pids", usage = "Comma-separated list of audio PIDs to retain (default: all)")
		String audioPids;

		@Option(name = "--subtitle-pids", usage = "Comma-separated list of subtitle PIDs to retain (default: all)")
		String subtitlePids;

	}

	public static class Extract extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "playlist-to-mkv";
		}

		@Override
		public String getDescription() {
			return "Extract a Blu-ray playlist to an MKV container";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			Path playlistPath = opts.playlist.toPath();
			Path streamDir = opts.streamDir.toPath();
			Path outputMkv = opts.output.toPath();

			MoviePlaylist playlist = new MoviePlaylistParser().parse(playlistPath);

			Set<Integer> audioPids = parsePidList(opts.audioPids);
			Set<Integer> subtitlePids = parsePidList(opts.subtitlePids);

			new PlaylistToMkvExtractor().extract(streamDir, playlist, outputMkv, audioPids, subtitlePids);
			System.out.println("Playlist→MKV complete → " + outputMkv);
		}

	}

	private static Set<Integer> parsePidList(String pids) {
		if (pids == null || pids.isBlank())
			return null;
		return Arrays.stream(pids.split(",")).map(String::trim)
				.map(s -> s.startsWith("0x") || s.startsWith("0X") ? Integer.parseInt(s.substring(2), 16)
						: Integer.parseInt(s))
				.collect(Collectors.toCollection(HashSet::new));
	}

}
