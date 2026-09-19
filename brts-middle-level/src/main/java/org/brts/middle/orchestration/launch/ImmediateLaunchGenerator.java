package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/ImmediateLaunchGenerator.java' is part of BRTS.
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

import lombok.RequiredArgsConstructor;

/**
 * An implementation of {@link AbstractDeferredLaunchGenerator} that immediately executes each CLI invocation, and
 * outputs messages/comments to the standard error.
 */
@RequiredArgsConstructor
public class ImmediateLaunchGenerator extends AbstractDeferredLaunchGenerator {
	private final BrtsImmediateInvocator invocator;

	@Override
	protected void invoke(BrtsCliInvocation invocation) {
		// run in process with BrtsMain
		// so we don't worry about paths
		// and we don't have to wait for JVM startup for each invocation
		try {
			invocator.invoke(invocation.toTokens().toArray(new String[0]));
		} catch (Exception e) {
			throw new RuntimeException("Failed to invoke BRTS CLI", e);
		}
	}

	@Override
	public void comment(String text) {
		System.err.println("# " + text);
	}

	@Override
	public void message(String text) {
		System.err.println(text);
	}

	@Override
	public Path write(Path outputDir) throws IOException {
		// nothing to write, since this generator executes immediately
		return null;
	}
}
