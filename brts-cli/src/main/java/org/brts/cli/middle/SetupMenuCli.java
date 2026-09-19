package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/middle/SetupMenuCli.java' is part of BRTS.
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
import org.brts.middle.menu.SetupMenuGenerator;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for the middle-level "create-setup-menu" command.
 */
public class SetupMenuCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the setup menu JSON descriptor")
		SetupMenuDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the generated M2TS and intermediate files")
		File outputDir;

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "create-setup-menu";
		}

		@Override
		public String getDescription() {
			return "Generate a Blu-ray setup/settings menu M2TS";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			System.out.println("Generating setup menu from: " + opts.descriptor);
			System.out.println("Output directory: " + opts.outputDir);

			SetupMenuGenerator generator = new SetupMenuGenerator();
			generator.generate(opts.descriptor, opts.outputDir.toPath());

			System.out.println("Setup menu generation complete. Check " + opts.outputDir);
		}

	}

}
