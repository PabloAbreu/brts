package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.mkv.MkvToPlaylistConverter;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI for converting an MKV file into a Blu-ray clip triplet (M2TS + CLPI + MPLS).
 */
public class MkvToPlaylistCli {

	static class ConvertOptions {

		@Option(name = "--input", required = true, usage = "Path to the MKV source file")
		File input;

		@Option(name = "--output", required = true,
				usage = "Output directory for BDMV structure (STREAM/, CLIPINF/, PLAYLIST/ subdirs)")
		File outputDir;

		@Option(name = "--clip-name", usage = "5-digit clip name (default: 00001)")
		String clipName = "00001";

		@Option(name = "--audio-tracks", usage = "Comma-separated MKV track numbers for audio (default: all)")
		String audioTracks;

		@Option(name = "--subtitle-tracks", usage = "Comma-separated MKV track numbers for subtitles (default: all)")
		String subtitleTracks;

		@Option(name = "--pgs-resolution", usage = "PGS render resolution as WIDTHxHEIGHT (default: 1920x1080)")
		String pgsResolution;

		@Option(name = "--pgs-font-name", usage = "Font for PGS subtitle rendering (default: SansSerif)")
		String pgsFontName;

		@Option(name = "--pgs-font-size", usage = "Font size for PGS subtitle rendering (default: 48)")
		Integer pgsFontSize;

		@Option(name = "--pgs-font-color", usage = "Text colour for PGS as RRGGBB hex (default: FFFFFF)")
		String pgsFontColor;

		@Option(name = "--pgs-outline-color", usage = "Outline colour for PGS as RRGGBB hex (default: 000000)")
		String pgsOutlineColor;

	}

	public static class Convert extends FeatureRunner<ConvertOptions> {

		@Override
		public String getCommandName() {
			return "mkv-to-playlist";
		}

		@Override
		public String getDescription() {
			return "Convert an MKV file to Blu-ray M2TS/CLPI/MPLS";
		}

		@Override
		protected ConvertOptions createOptions() {
			return new ConvertOptions();
		}

		@Override
		protected void execute(ConvertOptions opts) throws Exception {
			MkvToPlaylistConverter.Config config = new MkvToPlaylistConverter.Config(opts.input.toPath(),
					opts.outputDir.toPath(), opts.clipName);

			if (opts.audioTracks != null && !opts.audioTracks.isBlank()) {
				config.setAudioTrackFilter(parseTrackNumbers(opts.audioTracks));
			}

			if (opts.subtitleTracks != null && !opts.subtitleTracks.isBlank()) {
				config.setSubtitleTrackFilter(parseTrackNumbers(opts.subtitleTracks));
			}

			PgsRenderConfig pgsConfig = new PgsRenderConfig();
			if (opts.pgsResolution != null) {
				String[] parts = opts.pgsResolution.toLowerCase().split("x");
				if (parts.length == 2) {
					pgsConfig.setScreenWidth(Integer.parseInt(parts[0].trim()));
					pgsConfig.setScreenHeight(Integer.parseInt(parts[1].trim()));
				}
			}
			if (opts.pgsFontName != null)
				pgsConfig.setFontName(opts.pgsFontName);
			if (opts.pgsFontSize != null)
				pgsConfig.setFontSize(opts.pgsFontSize);
			if (opts.pgsFontColor != null) {
				pgsConfig.setFontColor(0xFF000000 | Integer.parseInt(opts.pgsFontColor, 16));
			}
			if (opts.pgsOutlineColor != null) {
				pgsConfig.setOutlineColor(0xFF000000 | Integer.parseInt(opts.pgsOutlineColor, 16));
			}
			config.setPgsConfig(pgsConfig);

			MkvToPlaylistConverter converter = new MkvToPlaylistConverter();
			MkvToPlaylistConverter.Result result = converter.convert(config);

			System.out.println("M2TS written  → " + result.m2tsFile());
			System.out.println("CLPI written  → " + result.clpiFile());
			System.out.println("MPLS written  → " + result.mplsFile());
		}

	}

	private static Set<Integer> parseTrackNumbers(String csv) {
		return Arrays.stream(csv.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(Integer::parseInt)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
