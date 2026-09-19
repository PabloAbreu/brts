package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/ClipInfoCli.java' is part of BRTS.
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
