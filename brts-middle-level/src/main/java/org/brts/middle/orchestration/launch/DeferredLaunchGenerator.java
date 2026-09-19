package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/DeferredLaunchGenerator.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Accumulates the sequence of low-level build steps produced by the middle-level orchestrator and renders them into a
 * deferred-launch artifact (e.g. a shell script) that the user can run later to actually produce the disc.
 * <p>
 * Implementations decide how each semantic step is rendered (bash, PowerShell, direct in-process invocation, ...),
 * keeping the orchestrator itself free of any launch-mechanism-specific logic.
 */
public interface DeferredLaunchGenerator {

	/** Adds a human-readable comment/section marker ahead of the next step(s). */
	void comment(String text);

	/** Records an mkv-to-playlist conversion step. */
	void mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir);

	/** Records a title-menu creation step. */
	void createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir);

	/** Records an index.bdmv write step. */
	void indexWrite(Path indexFile, Path bdmvOutputDir);

	/** Records a MovieObject.bdmv write step. */
	void mobjWrite(Path moviesFile, Path bdmvOutputDir);

	/** Records a final user-facing message (e.g. a completion notice). */
	void message(String text);

	/**
	 * Finalizes and persists the launch artifact under {@code outputDir}.
	 *
	 * @return the path of the generated artifact
	 */
	Path write(Path outputDir) throws IOException;

}
