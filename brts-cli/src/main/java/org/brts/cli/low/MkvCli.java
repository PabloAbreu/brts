package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.lowlevel.mkv.MkvExtractor;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI for MKV source media inspection.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>mkv-info</b>: parse an MKV file and emit the discovered track metadata as JSON</li>
 * <li><b>mkv-extract</b>: demux selected tracks into elementary stream files</li>
 * </ul>
 */
public class MkvCli {

	public static class InfoOptions {

		@Option(name = "--input", required = true, usage = "Path to the MKV file to inspect")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class ExtractOptions {

		@Option(name = "--input", required = true, usage = "Path to the MKV source file")
		File input;

		@Option(name = "--output", required = true, usage = "Output directory for elementary stream files")
		File outputDir;

		@Option(name = "--track-numbers", usage = "Comma-separated list of MKV track numbers to extract (default: all)")
		String trackNumbers;

		@Option(name = "--video-only", usage = "Extract only video tracks")
		boolean videoOnly;

		@Option(name = "--audio-only", usage = "Extract only audio tracks")
		boolean audioOnly;

		@Option(name = "--subtitles-only", usage = "Extract only subtitle tracks")
		boolean subtitlesOnly;

	}

	public static class Info extends FeatureRunner<InfoOptions> {

		@Override
		public String getCommandName() {
			return "mkv-info";
		}

		@Override
		public String getDescription() {
			return "Inspect an MKV file and emit track metadata as JSON";
		}

		@Override
		protected void execute(InfoOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			SourceMediaInfo info = new MkvSourceMediaParser().parse(inputPath);
			writeJson(opts.output, info);
			if (opts.output != null) {
				System.out.println("Parsed MKV → " + opts.output);
			}
		}

	}

	public static class Extract extends FeatureRunner<ExtractOptions> {

		@Override
		public String getCommandName() {
			return "mkv-extract";
		}

		@Override
		public String getDescription() {
			return "Demux elementary streams from an MKV file";
		}

		@Override
		protected void execute(ExtractOptions opts) throws Exception {
			MkvExtractor extractor = new MkvExtractor();
			MkvExtractor.Config config = new MkvExtractor.Config(opts.input.toPath(), opts.outputDir.toPath());

			if (opts.trackNumbers != null && !opts.trackNumbers.isBlank()) {
				config.setTrackNumbers(parseTrackNumbers(opts.trackNumbers));
			}
			config.setVideoOnly(opts.videoOnly);
			config.setAudioOnly(opts.audioOnly);
			config.setSubtitlesOnly(opts.subtitlesOnly);

			MkvExtractor.Result result = extractor.extract(config);

			System.out.println("Extraction complete → " + opts.outputDir + " (" + result.selectedTrackNumbers().size()
					+ " track(s))");
			for (Map.Entry<Integer, Path> entry : result.extractedFiles().entrySet()) {
				System.out.println("  Track " + entry.getKey() + " → " + entry.getValue().getFileName());
			}
		}

	}

	private static Set<Integer> parseTrackNumbers(String csv) {
		try {
			return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt)
					.collect(Collectors.toCollection(LinkedHashSet::new));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid --track-numbers value: '" + csv + "'", e);
		}
	}

}
