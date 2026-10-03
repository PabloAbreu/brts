package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/JsonDeferredLaunchGenerator.java' is part of BRTS.
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

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;

/**
 * Renders the deferred launch as a {@link BatchRunDescriptor} JSON file, to be run later with
 * {@code brts low batch-run --descriptor orchestrate.json}.
 */
public class JsonDeferredLaunchGenerator extends AbstractDeferredLaunchGenerator {

	public static final String FILE_NAME = "orchestrate.json";

	private final BatchRunDescriptor descriptor = new BatchRunDescriptor();

	@Override
	public void comment(String text) {
		descriptor.getSteps().add(BatchStep.comment(text));
	}

	@Override
	protected void invoke(BrtsCliInvocation invocation) {
		descriptor.getSteps().add(invocation.toBatchStep());
	}

	@Override
	public void message(String text) {
		descriptor.getSteps().add(BatchStep.message(text));
	}

	@Override
	public Path write(Path outputDir) throws IOException {
		Path path = outputDir.resolve(FILE_NAME);
		JsonMapperFactory.get().writeValue(path.toFile(), descriptor);
		return path;
	}

}
