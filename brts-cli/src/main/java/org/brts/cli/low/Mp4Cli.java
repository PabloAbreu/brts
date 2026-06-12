package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.mp4.Mp4SourceMediaParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;

/**
 * CLI for MP4/MOV source media inspection.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>mp4-info</b>: probe an MP4/MOV file and emit the discovered track metadata as JSON</li>
 * </ul>
 */
public class Mp4Cli {

	public static class InfoOptions {

		@Option(name = "--input", required = true, usage = "Path to the MP4/MOV file to inspect")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class Info extends FeatureRunner<InfoOptions> {

		@Override
		public String getCommandName() {
			return "mp4-info";
		}

		@Override
		public String getDescription() {
			return "Inspect an MP4/MOV file and emit track metadata as JSON";
		}

		@Override
		protected void execute(InfoOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			SourceMediaInfo info = new Mp4SourceMediaParser().parse(inputPath);
			writeJson(opts.output, info);
			if (opts.output != null) {
				System.out.println("Parsed MP4 \u2192 " + opts.output);
			}
		}

	}

}
