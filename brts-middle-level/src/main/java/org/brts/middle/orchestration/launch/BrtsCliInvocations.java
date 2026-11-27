/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/BrtsCliInvocations.java' is part of BRTS.
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

package org.brts.middle.orchestration.launch;

import java.nio.file.Path;

import org.brts.common.orchestration.BrtsInvocation;

/** Factories for the CLI commands used by middle-level orchestration. */
public final class BrtsCliInvocations {
	static final String LOW = "low";

	private BrtsCliInvocations() {
	}

	private static String toAbsolute(Path path) {
		return path.toAbsolutePath().toString();
	}

	public static BrtsInvocation mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir) {
		return BrtsInvocation.builder(LOW, "mkv-to-playlist").argument("--descriptor", toAbsolute(descriptorFile))
				.argument("--output", toAbsolute(bdmvOutputDir)).build();
	}

	public static BrtsInvocation createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir) {
		return BrtsInvocation.builder(LOW, "create-title-menu").argument("--descriptor", toAbsolute(descriptorFile))
				.argument("--output", toAbsolute(discOutputDir)).argument("--base-dir", toAbsolute(baseDir)).build();
	}

	public static BrtsInvocation indexWrite(Path indexFile, Path bdmvOutputDir) {
		return BrtsInvocation.builder(LOW, "index-write").argument("--input", toAbsolute(indexFile))
				.argument("--output", toAbsolute(bdmvOutputDir)).build();
	}

	public static BrtsInvocation mobjWrite(Path moviesFile, Path bdmvOutputDir) {
		return BrtsInvocation.builder(LOW, "mobj-write").argument("--input", toAbsolute(moviesFile))
				.argument("--output", toAbsolute(bdmvOutputDir)).build();
	}

}
