package org.brts.cli.low;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.bdmv.MovieObjectsWriter;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.parser.MovieObjectsParser;
import org.kohsuke.args4j.Option;

/**
 * CLI for low-level {@code BDMV/MovieObject.bdmv} operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>mobj-parse</b> — parse a binary {@code MovieObject.bdmv} file and emit JSON</li>
 * <li><b>mobj-write</b> — generate a binary {@code MovieObject.bdmv} from a JSON model</li>
 * </ul>
 */
public class MovieObjectsCli {

	// -------------------------------------------------------------------------
	// Parse command: binary MovieObject.bdmv → JSON
	// -------------------------------------------------------------------------

	public static class ParseOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the binary MovieObject.bdmv file to parse")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (omit to print to stdout)")
		File output;

	}

	public static class Parse extends FeatureRunner<ParseOptions> {

		@Override
		public String getCommandName() {
			return "mobj-parse";
		}

		@Override
		public String getDescription() {
			return "Parse a binary MovieObject.bdmv file to JSON";
		}

		@Override
		protected void execute(ParseOptions opts) throws Exception {
			MovieObjects mobj = new MovieObjectsParser().parse(opts.input.toPath());
			if (opts.output != null && opts.output.getParentFile() != null) {
				opts.output.getParentFile().mkdirs();
			}
			writeJson(opts.output, mobj);
			if (opts.output != null) {
				System.out.println("Parsed MovieObject.bdmv → " + opts.output);
			}
		}

	}

	// -------------------------------------------------------------------------
	// Write command: JSON → binary MovieObject.bdmv
	// -------------------------------------------------------------------------

	public static class WriteOptions extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--input", required = true, usage = "Path to the JSON model file (as produced by mobj-parse)")
		MovieObjects input;

		@Option(name = "--output", required = true, usage = "Output path for the generated MovieObject.bdmv file")
		File output;

	}

	public static class Write extends FeatureRunner<WriteOptions> {

		@Override
		public String getCommandName() {
			return "mobj-write";
		}

		@Override
		public String getDescription() {
			return "Generate a binary MovieObject.bdmv from a JSON model";
		}

		@Override
		protected void execute(WriteOptions opts) throws Exception {
			File dest = opts.output;
			if (dest.isDirectory())
				dest = new File(dest, "MovieObject.bdmv");
			else if (dest.getParentFile() != null) {
				dest.getParentFile().mkdirs();
			}
			try (OutputStream out = new FileOutputStream(dest)) {
				new MovieObjectsWriter().write(opts.input, out);
			}
			System.out.println("Wrote MovieObject.bdmv → " + dest);
		}

	}

}
