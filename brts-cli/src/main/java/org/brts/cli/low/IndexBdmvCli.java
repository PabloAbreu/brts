package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/IndexBdmvCli.java' is part of BRTS.
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
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.bdmv.IndexBdmvWriter;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * CLI for low-level {@code BDMV/index.bdmv} operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>index-parse</b> — parse a binary {@code index.bdmv} file and emit JSON</li>
 * <li><b>index-write</b> — generate a binary {@code index.bdmv} from a JSON model</li>
 * </ul>
 */
public class IndexBdmvCli {

	// -------------------------------------------------------------------------
	// Parse command: binary index.bdmv → JSON
	// -------------------------------------------------------------------------

	public static class ParseOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the binary index.bdmv file to parse")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (omit to print to stdout)")
		File output;

	}

	public static class Parse extends FeatureRunner<ParseOptions> {

		@Override
		public String getCommandName() {
			return "index-parse";
		}

		@Override
		public String getDescription() {
			return "Parse a binary index.bdmv file to JSON";
		}

		@Override
		protected void execute(ParseOptions opts) throws Exception {
			IndexBdmv index = new IndexBdmvParser().parse(opts.input.toPath());
			if (opts.output != null) {
				if (opts.output.getParentFile() != null) {
					opts.output.getParentFile().mkdirs();
				}
			}
			writeJson(opts.output, index);
			if (opts.output != null) {
				System.out.println("Parsed index.bdmv → " + opts.output);
			}
		}

	}

	// -------------------------------------------------------------------------
	// Write command: JSON → binary index.bdmv
	// -------------------------------------------------------------------------

	public static class WriteOptions extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--input", required = true, usage = "Path to the JSON model file (as produced by index-parse)")
		IndexBdmv input;

		@Option(name = "--output", required = true, usage = "Output path for the generated index.bdmv file")
		File output;

	}

	public static class Write extends FeatureRunner<WriteOptions> {

		@Override
		public String getCommandName() {
			return "index-write";
		}

		@Override
		public String getDescription() {
			return "Generate a binary index.bdmv from a JSON model";
		}

		@Override
		protected void execute(WriteOptions opts) throws Exception {
			File dest = opts.output;
			if (dest.isDirectory())
				dest = new File(dest, "index.bdmv");
			else if (dest.getParentFile() != null) {
				dest.getParentFile().mkdirs();
			}
			try (OutputStream out = new FileOutputStream(dest)) {
				new IndexBdmvWriter().write(opts.input, out);
			}
			System.out.println("Wrote index.bdmv → " + dest);
		}

	}

}
