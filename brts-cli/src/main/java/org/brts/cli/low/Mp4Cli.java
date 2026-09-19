package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/Mp4Cli.java' is part of BRTS.
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

	public static class InfoOptions extends org.brts.cli.BaseOptions {

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
