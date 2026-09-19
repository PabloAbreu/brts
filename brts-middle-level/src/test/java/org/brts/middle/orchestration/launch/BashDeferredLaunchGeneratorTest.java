package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/test/java/org/brts/middle/orchestration/launch/BashDeferredLaunchGeneratorTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashDeferredLaunchGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void write_producesScriptWithHeaderCommentsInvocationsAndMessage() throws IOException {
		BashDeferredLaunchGenerator generator = new BashDeferredLaunchGenerator();
		Path descriptor = tempDir.resolve("00001-mkv-descriptor.json");
		Path bdmv = tempDir.resolve("BDMV");

		generator.comment("Title 1");
		generator.mkvToPlaylist(descriptor, bdmv);
		generator.indexWrite(tempDir.resolve("index.json"), bdmv);
		generator.message("Done.");

		Path scriptPath = generator.write(tempDir);
		String content = Files.readString(scriptPath);

		assertThat(content).startsWith("#!/usr/bin/env bash");
		assertThat(content).contains("# Title 1");
		assertThat(content).contains("$BRTS_CLI low mkv-to-playlist --descriptor " + descriptor.toAbsolutePath()
				+ " --output " + bdmv.toAbsolutePath());
		assertThat(content).contains("$BRTS_CLI low index-write --input "
				+ tempDir.resolve("index.json").toAbsolutePath() + " --output " + bdmv.toAbsolutePath());
		assertThat(content).contains("echo \"Done.\"");
		// every invocation, including index-write, now gets a trailing blank line
		assertThat(content).contains("$BRTS_CLI low index-write --input "
				+ tempDir.resolve("index.json").toAbsolutePath() + " --output " + bdmv.toAbsolutePath() + "\n\n");
		assertThat(scriptPath.toFile().canExecute()).isTrue();
	}

}
