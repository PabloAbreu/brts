package org.brts.cli;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/BaseOptions.java' is part of BRTS.
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

import org.kohsuke.args4j.Option;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseOptions {
	public static final String OPTION_NO_BANNER = "--no-banner";

	@Option(name = "--error-details", required = false, hidden = true, usage = "Shows more detailed errors. This option should be put first.")
	boolean errorDetails;

	@Option(name = OPTION_NO_BANNER, required = false, hidden = true, usage = "Suppresses the startup banner. This option should be put first.")
	boolean noBanner;

}
