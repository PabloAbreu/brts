package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/TitleMenuCli.java' is part of BRTS.
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
import org.brts.lowlevel.titlemenu.TitleMenuGenerator;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.kohsuke.args4j.Option;

/**
 * CLI for the low-level "create-title-menu" command.
 * <p>
 * Generates a Blu-ray title selection menu (MPLS + 2 M2TS/CLPI pairs) from a JSON descriptor.
 */
public class TitleMenuCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the title menu JSON descriptor")
		TitleMenuDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the generated BDMV structure")
		File outputDir;

		@Option(name = "--base-dir", required = false, usage = "Base directory for resolving relative paths in the descriptor (defaults to descriptor's parent)")
		File baseDir;

	}

	public static class Create extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "create-title-menu";
		}

		@Override
		public String getDescription() {
			return "Generate a Blu-ray title selection menu (MPLS + M2TS/CLPI)";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			System.out.println("Generating title menu...");
			System.out.println("Output directory: " + opts.outputDir);

			Path baseDir = opts.baseDir != null ? opts.baseDir.toPath() : opts.outputDir.toPath().toAbsolutePath();

			TitleMenuGenerator generator = new TitleMenuGenerator();
			generator.generate(opts.descriptor, opts.outputDir.toPath(), baseDir);

			System.out.println("Title menu generation complete. Check " + opts.outputDir);
		}

	}

}
