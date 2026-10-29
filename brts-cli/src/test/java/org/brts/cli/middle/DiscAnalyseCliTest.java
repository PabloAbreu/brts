/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/middle/DiscAnalyseCliTest.java' is part of BRTS.
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

package org.brts.cli.middle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscAnalyseCliTest {

	@TempDir
	Path tempDir;

	@Test
	void commandIsRegisteredInMiddleDispatcher() {
		assertThat(MiddleLevelCli.getLevelDispatcher().getRunners())
				.anyMatch(runner -> "disc-analyse".equals(runner.getCommandName()));
	}

	@Test
	void parserFailureStopsBatchAndStillWritesPartialReport() throws Exception {
		Path bdmv = Files.createDirectories(tempDir.resolve("disc/BDMV"));
		Files.createDirectories(bdmv.resolve("PLAYLIST"));
		Files.createDirectories(bdmv.resolve("CLIPINF"));
		Files.createDirectories(bdmv.resolve("STREAM"));
		Files.writeString(bdmv.resolve("index.bdmv"), "not a valid index");
		Files.writeString(bdmv.resolve("MovieObject.bdmv"), "placeholder");
		Files.writeString(bdmv.resolve("PLAYLIST/00001.mpls"), "placeholder");
		Files.writeString(bdmv.resolve("CLIPINF/00001.clpi"), "placeholder");
		Files.writeString(bdmv.resolve("STREAM/00001.m2ts"), "placeholder");
		Path output = tempDir.resolve("report");

		assertThatThrownBy(() -> new DiscAnalyseCli.Run()
				.run(new String[] { "--disc-root", tempDir.resolve("disc").toString(), "--output", output.toString() }))
				.isInstanceOf(Exception.class).hasMessageContaining("Disc analysis stopped at");

		assertThat(output.resolve("index.html")).exists();
		assertThat(Files.readString(output.resolve("index.html"))).contains("Step 1/5 failed");
		assertThat(output.resolve("batch-run.json")).exists();
		assertThat(output.resolve("BDMV/PLAYLIST/00001.json")).doesNotExist();
	}
}
