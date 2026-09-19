package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/middle/SimpleBuildCli.java' is part of BRTS.
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
import org.brts.cli.OrchestratorOptions;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.SimpleBuildDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;

import jakarta.validation.Valid;

/**
 * Build a single-title disc with no top menu, from a simplified descriptor.
 */
public class SimpleBuildCli {

	public static class SimpleBuildOptions extends OrchestratorOptions {
		@Valid
		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the simplified single-title JSON descriptor")
		SimpleBuildDescriptor descriptor;
	}

	public static class Run extends FeatureRunner<SimpleBuildOptions> {

		@Override
		public String getCommandName() {
			return "simple-build";
		}

		@Override
		public String getDescription() {
			return "Build a single-title Blu-ray disc with no top menu, from a simplified descriptor";
		}

		@Override
		protected void execute(SimpleBuildOptions opts) throws Exception {
			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator orchestrator = new MiddleLevelOrchestrator(titleBuilder,
					opts.getLaunchGenerator(getInvocator()));

			orchestrator.orchestrate(opts.descriptor.toDiscDescriptor(), opts.outputDir);

			System.out.println("Middle-level orchestration complete.");
		}

	}

}
