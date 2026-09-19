package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/DiscCli.java' is part of BRTS.
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
import org.brts.lowlevel.model.disc.LowLevelDiscDescriptor;

import lombok.extern.slf4j.Slf4j;

/**
 * CLI for low-level disc operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>disc-create</b> — Create a full disc</li>
 * </ul>
 */
@Slf4j
public class DiscCli {

	// -------------------------------------------------------------------------
	// Create command: full descriptor -> full Disc
	// -------------------------------------------------------------------------

	public static class CreateOptions extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--input", required = true, usage = "Path to the disc descriptor")
		LowLevelDiscDescriptor input;

	}

	public static class Create extends FeatureRunner<CreateOptions> {

		@Override
		public String getCommandName() {
			return "disc-create";
		}

		@Override
		public String getDescription() {
			return "Create a full disc from a descriptor and video files";
		}

		@Override
		protected void execute(CreateOptions opts) throws Exception {
			LowLevelDiscDescriptor descriptor = opts.input;
			log.debug("Creation of disc " + descriptor.getDiscName());
		}

	}

}
