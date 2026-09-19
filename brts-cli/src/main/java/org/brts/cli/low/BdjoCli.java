package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/BdjoCli.java' is part of BRTS.
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
import org.brts.lowlevel.bdmv.BdjoWriter;
import org.brts.lowlevel.model.bdmv.Bdjo;
import org.brts.lowlevel.parser.BdjoParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * CLI for low-level {@code BDMV/BDJO/XXXXX.bdjo} operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>bdjo-parse</b> — parse a binary {@code .bdjo} file and emit JSON</li>
 * <li><b>bdjo-write</b> — generate a binary {@code .bdjo} from a JSON model</li>
 * </ul>
 */
public class BdjoCli {

	// -------------------------------------------------------------------------
	// Parse command: binary .bdjo → JSON
	// -------------------------------------------------------------------------

	public static class ParseOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the binary .bdjo file to parse")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (omit to print to stdout)")
		File output;

	}

	public static class Parse extends FeatureRunner<ParseOptions> {

		@Override
		public String getCommandName() {
			return "bdjo-parse";
		}

		@Override
		public String getDescription() {
			return "Parse a binary .bdjo file to JSON";
		}

		@Override
		protected void execute(ParseOptions opts) throws Exception {
			Bdjo bdjo = new BdjoParser().parse(opts.input.toPath());
			if (opts.output != null && opts.output.getParentFile() != null) {
				opts.output.getParentFile().mkdirs();
			}
			writeJson(opts.output, bdjo);
			if (opts.output != null) {
				System.out.println("Parsed .bdjo → " + opts.output);
			}
		}

	}

	// -------------------------------------------------------------------------
	// Write command: JSON → binary .bdjo
	// -------------------------------------------------------------------------

	static class WriteOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the JSON model file (as produced by bdjo-parse)")
		File input;

		@Option(name = "--output", required = true, usage = "Output path for the generated .bdjo file")
		File output;

	}

	public static class Write extends FeatureRunner<WriteOptions> {

		@Override
		public String getCommandName() {
			return "bdjo-write";
		}

		@Override
		public String getDescription() {
			return "Generate a binary .bdjo from a JSON model";
		}

		@Override
		protected WriteOptions createOptions() {
			return new WriteOptions();
		}

		@Override
		protected void execute(WriteOptions opts) throws Exception {
			Bdjo bdjo = loadJson(opts.input, Bdjo.class);
			if (opts.output.getParentFile() != null) {
				opts.output.getParentFile().mkdirs();
			}
			try (OutputStream out = new FileOutputStream(opts.output)) {
				new BdjoWriter().write(bdjo, out);
			}
			System.out.println("Wrote .bdjo → " + opts.output);
		}

	}

}
