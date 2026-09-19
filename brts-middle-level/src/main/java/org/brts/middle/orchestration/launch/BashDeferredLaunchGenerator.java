package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/launch/BashDeferredLaunchGenerator.java' is part of BRTS.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.utils.ProcessUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders the deferred launch as a bash script invoking the BRTS CLI, to be run later by the user.
 */
@Slf4j
public class BashDeferredLaunchGenerator extends AbstractDeferredLaunchGenerator {

	private final List<String> lines = new ArrayList<>();

	public BashDeferredLaunchGenerator() {
		lines.add("#!/usr/bin/env bash");
		lines.add("# Auto-generated middle-level orchestration script");
		lines.add("# Run each step in order to produce the low-level Blu-ray files");
		lines.add("# 'java' must be in your PATH");
		lines.add("set -euo pipefail");
		lines.add("");
		lines.add("BRTS_CLI=\"" + ProcessUtils.getBrtsCommand() + "\"");
		lines.add("");
	}

	@Override
	public void comment(String text) {
		lines.add("# " + text);
	}

	@Override
	protected void invoke(BrtsCliInvocation invocation) {
		lines.add("$BRTS_CLI " + String.join(" ", invocation.toTokens()));
		lines.add("");
	}

	@Override
	public void message(String text) {
		lines.add("echo \"" + text + "\"");
	}

	@Override
	public Path write(Path outputDir) throws IOException {
		Path scriptPath = outputDir.resolve("orchestrate.sh");
		Files.writeString(scriptPath, String.join("\n", lines) + "\n");
		scriptPath.toFile().setExecutable(true);
		return scriptPath;
	}

}
