package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/AbstractDeferredLaunchGenerator.java' is part of BRTS.
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

/**
 * Translates the semantic {@link DeferredLaunchGenerator} build steps into {@link BrtsCliInvocation} instances, keeping
 * the knowledge of each command's level/name/arguments independent of how it is ultimately rendered.
 */
public abstract class AbstractDeferredLaunchGenerator implements DeferredLaunchGenerator {

	/** Renders a single resolved CLI invocation using this generator's syntax. */
	protected abstract void invoke(BrtsCliInvocation invocation);

	@Override
	public void mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.mkvToPlaylist(descriptorFile, bdmvOutputDir));
	}

	@Override
	public void createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir) {
		invoke(BrtsCliInvocation.createTitleMenu(descriptorFile, discOutputDir, baseDir));
	}

	@Override
	public void indexWrite(Path indexFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.indexWrite(indexFile, bdmvOutputDir));
	}

	@Override
	public void mobjWrite(Path moviesFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.mobjWrite(moviesFile, bdmvOutputDir));
	}

}
