package org.brts.cli.low;

import java.io.File;
import java.nio.file.Path;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.kohsuke.args4j.Option;

/**
 * CLI for low-level CLPI (Clip Information) operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>clip-parse</b>: parse a {@code .clpi} binary file → JSON descriptor</li>
 * <li><b>clip-write</b>: generate a {@code .clpi} binary from a JSON descriptor</li>
 * </ul>
 */
public class ClipInfoCli {

	// -------------------------------------------------------------------------
	// Parse command
	// -------------------------------------------------------------------------

	public static class ParseOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the .clpi file to parse")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class Parse extends FeatureRunner<ParseOptions> {

		@Override
		public String getCommandName() {
			return "clip-parse";
		}

		@Override
		public String getDescription() {
			return "Parse a .clpi binary file to JSON";
		}

		@Override
		protected void execute(ParseOptions opts) throws Exception {
			ClipInfo clipInfo = new ClipInfoParser().parse(opts.input.toPath());
			writeJson(opts.output, clipInfo);
			if (opts.output != null) {
				System.out.println("Parsed CLPI → " + opts.output);
			}
		}

	}

	// -------------------------------------------------------------------------
	// Write command
	// -------------------------------------------------------------------------

	public static class WriteOptions extends org.brts.cli.BaseOptions {

		// @Option(name = "--descriptor", required = true, usage = "Path to the clip JSON
		// descriptor")
		// File descriptor;
		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the clip JSON descriptor")
		ClipInfo descriptor;

		@Option(name = "--output", required = true, usage = "Output directory (BDMV/CLIPINF/ recommended)")
		File outputDir;

	}

	public static class Write extends FeatureRunner<WriteOptions> {

		@Override
		public String getCommandName() {
			return "clip-write";
		}

		@Override
		public String getDescription() {
			return "Generate a .clpi binary from a JSON descriptor";
		}

		@Override
		protected void execute(WriteOptions opts) throws Exception {
			ClipInfo clipInfo = opts.descriptor;// loadJson(opts.descriptor,
												// ClipInfo.class);
			ClipInfoWriter writer = new ClipInfoWriter();
			Path outputPath = opts.outputDir.toPath().resolve(clipInfo.getClipName() + ".clpi");
			writer.write(clipInfo, outputPath);
			System.out.println("Wrote CLPI → " + outputPath);
		}

	}

}
