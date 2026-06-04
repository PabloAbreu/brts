package org.brts.cli.low;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.lowlevel.writer.MoviePlaylistWriter;
import org.kohsuke.args4j.Option;

/**
 * CLI for low-level MPLS (Movie Playlist) operations.
 */
public class PlaylistCli {

	public static class ParseOptions {

		@Option(name = "--input", required = true, usage = "Path to the .mpls file to parse")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class Parse extends FeatureRunner<ParseOptions> {

		@Override
		public String getCommandName() {
			return "playlist-parse";
		}

		@Override
		public String getDescription() {
			return "Parse a .mpls binary file to JSON";
		}

		@Override
		protected void execute(ParseOptions opts) throws Exception {
			MoviePlaylist playlist = new MoviePlaylistParser().parse(opts.input.toPath());
			writeJson(opts.output, playlist);
			if (opts.output != null) {
				System.out.println("Parsed MPLS → " + opts.output);
			}
		}

	}

	public static class WriteOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the playlist JSON descriptor")
		MoviePlaylist descriptor;

		@Option(name = "--output", required = true, usage = "Output directory (BDMV/PLAYLIST/ recommended)")
		File outputDir;

	}

	public static class Write extends FeatureRunner<WriteOptions> {

		@Override
		public String getCommandName() {
			return "playlist-write";
		}

		@Override
		public String getDescription() {
			return "Generate a .mpls binary from a JSON descriptor";
		}

		@Override
		protected void execute(WriteOptions opts) throws Exception {
			MoviePlaylist desc = opts.descriptor;
			MoviePlaylistWriter writer = new MoviePlaylistWriter();
			Path outputPath = opts.outputDir.toPath().resolve(desc.getPlaylistName() + ".mpls");
			writer.write(desc, outputPath);
			System.out.println("Wrote MPLS \u2192 " + outputPath);
		}

	}

	static class FindPlaylistOptions {

		@Option(name = "--clip", required = true, usage = "M2TS clip name to search for (e.g. 12345)")
		String clip;

		@Option(name = "--playlist-dir", required = true, usage = "Directory containing .mpls files (e.g. BDMV/PLAYLIST)")
		File playlistDir;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class FindPlaylist extends FeatureRunner<FindPlaylistOptions> {

		@Override
		public String getCommandName() {
			return "find-playlist";
		}

		@Override
		public String getDescription() {
			return "Find playlists referencing a given clip name";
		}

		@Override
		protected FindPlaylistOptions createOptions() {
			return new FindPlaylistOptions();
		}

		@Override
		protected void execute(FindPlaylistOptions opts) throws Exception {
			if (!opts.playlistDir.isDirectory()) {
				System.err.println("Not a directory: " + opts.playlistDir);
				System.exit(1);
			}

			MoviePlaylistParser parser = new MoviePlaylistParser();
			List<MoviePlaylist> results = new ArrayList<>();

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(opts.playlistDir.toPath(), "*.mpls")) {
				for (Path mplsPath : stream) {
					try {
						MoviePlaylist playlist = parser.parse(mplsPath);
						playlist.setPlaylistName(mplsPath.getFileName().toString().replaceFirst("\\.mpls$", ""));
						if (referencesClip(playlist, opts.clip)) {
							results.add(playlist);
						}
					} catch (IOException e) {
						System.err
								.println("Warning: failed to parse " + mplsPath.getFileName() + ": " + e.getMessage());
					}
				}
			}

			writeJson(opts.output, results);
			if (opts.output != null) {
				System.out.println("Found " + results.size() + " playlist(s) → " + opts.output);
			}
		}

	}

	private static boolean referencesClip(MoviePlaylist playlist, String clipName) {
		if (playlist.getPlayItems() != null) {
			for (PlayItem item : playlist.getPlayItems()) {
				if (clipName.equals(item.getClipName())) {
					return true;
				}
			}
		}
		if (playlist.getSubPaths() != null) {
			for (SubPath sp : playlist.getSubPaths()) {
				if (sp.getSubPlayItems() != null) {
					for (SubPath.SubPlayItem spi : sp.getSubPlayItems()) {
						if (clipName.equals(spi.getClipName())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

}
