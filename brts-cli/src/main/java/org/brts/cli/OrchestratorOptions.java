package org.brts.cli;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/OrchestratorOptions.java' is part of BRTS.
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

import java.nio.file.Path;

import org.brts.common.validation.WritableDirectory;
import org.brts.middle.orchestration.launch.BrtsImmediateInvocator;
import org.brts.middle.orchestration.launch.BashDeferredLaunchGenerator;
import org.brts.middle.orchestration.launch.DeferredLaunchGenerator;
import org.brts.middle.orchestration.launch.ImmediateLaunchGenerator;
import org.kohsuke.args4j.Option;

import jakarta.validation.constraints.AssertTrue;

public class OrchestratorOptions extends BaseOptions {

	public enum OutputType {
		// TODO one day add cmd or powershell for those who need that
		BASH, IMMEDIATE
	}

	@WritableDirectory(createIfMissing = true)
	@Option(name = "--output", required = false, usage = "Output directory for descriptors and script. Required when not using IMMEDIATE.")
	public Path outputDir;

	@Option(name = "--output-type", required = false, usage = "Type of output: BASH or IMMEDIATE")
	public OutputType outputType = OutputType.IMMEDIATE;

	@AssertTrue(message = "--output is required unless --output-type is IMMEDIATE")
	public boolean isOutputDirValid() {
		return outputType == OutputType.IMMEDIATE || outputDir != null;
	}

	public DeferredLaunchGenerator getLaunchGenerator(BrtsImmediateInvocator invocator) {
		if (outputType == OutputType.BASH) {
			return new BashDeferredLaunchGenerator();
		}
		return new ImmediateLaunchGenerator(invocator);
	}
}
