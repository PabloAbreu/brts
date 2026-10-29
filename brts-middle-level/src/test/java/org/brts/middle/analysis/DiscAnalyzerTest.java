/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/test/java/org/brts/middle/analysis/DiscAnalyzerTest.java' is part of BRTS.
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

package org.brts.middle.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscAnalyzerTest {

	@TempDir
	Path tempDir;

	private final DiscAnalyzer analyzer = new DiscAnalyzer();

	@Test
	void prepareCreatesOrderedBatchAndMirroredOutputFiles() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("report");

		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(plan.descriptor().toFile(),
				BatchRunDescriptor.class);

		assertThat(descriptor.getSteps()).extracting(step -> step.getCommand()).containsExactly("index-parse",
				"mobj-parse", "playlist-parse", "clip-parse", "m2ts-info");
		assertThat(descriptor.getSteps().get(0).toCommandTokens()).contains("--input",
				disc.resolve("BDMV/index.bdmv").toAbsolutePath().toString(), "--output",
				output.resolve("BDMV/index.json").toAbsolutePath().toString());
		assertThat(plan.files()).extracting(file -> output.relativize(file.json()).toString().replace('\\', '/'))
				.containsExactly("BDMV/index.json", "BDMV/MovieObject.json", "BDMV/PLAYLIST/00001.json",
						"BDMV/CLIPINF/00002.json", "BDMV/STREAM/00002.json");
		assertThat(output.resolve(DiscAnalyzer.STYLESHEET_NAME)).exists();
		assertThat(plan.descriptor()).exists();
	}

	@Test
	void prepareRejectsNonEmptyOutput() throws Exception {
		Path disc = createDisc();
		Path output = Files.createDirectory(tempDir.resolve("existing"));
		Files.writeString(output.resolve("keep.txt"), "keep");

		assertThatThrownBy(() -> analyzer.prepare(disc, output)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be empty");
		assertThat(output.resolve("keep.txt")).hasContent("keep");
	}

	@Test
	void htmlPagesContainCrossLinksAndEscapeParsedValues() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("report");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/index.json"), """
				{"titles":[{"objectType":1,"hdmvObjectId":0},{"objectType":2,"bdjObjectName":"APP01"}]}
				""");
		Files.writeString(output.resolve("BDMV/MovieObject.json"), """
				{"movieObjects":[{"navigationCommands":[{"mnemonic":"PLAY_PL","operand1":1}]}]}
				""");
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), """
				{"playItems":[{"clipName":"00002"}]}
				""");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), "{\"clipName\":\"00002\"}");
		Files.writeString(output.resolve("BDMV/STREAM/00002.json"), "{\"sourcePath\":\"<unsafe>\"}");

		analyzer.render(plan, List.of(), false);

		assertThat(Files.readString(output.resolve("index.html"))).contains("BDMV/index.html");
		assertThat(Files.readString(output.resolve("BDMV/index.html"))).contains("MovieObject.html#object-0",
				"BD-J object (not parsed)");
		assertThat(Files.readString(output.resolve("BDMV/MovieObject.html"))).contains("PLAYLIST/00001.html");
		assertThat(Files.readString(output.resolve("BDMV/PLAYLIST/00001.html"))).contains("../CLIPINF/00002.html",
				"../STREAM/00002.html");
		assertThat(Files.readString(output.resolve("BDMV/STREAM/00002.html"))).contains("&lt;unsafe&gt;");
		assertThat(output.resolve(DiscAnalyzer.STYLESHEET_NAME)).exists();
	}

	private Path createDisc() throws Exception {
		Path bdmv = Files.createDirectories(tempDir.resolve("disc/BDMV"));
		Files.createDirectories(bdmv.resolve("PLAYLIST"));
		Files.createDirectories(bdmv.resolve("CLIPINF"));
		Files.createDirectories(bdmv.resolve("STREAM"));
		Files.write(bdmv.resolve("index.bdmv"), new byte[0]);
		Files.write(bdmv.resolve("MovieObject.bdmv"), new byte[0]);
		Files.write(bdmv.resolve("PLAYLIST/00001.mpls"), new byte[0]);
		Files.write(bdmv.resolve("CLIPINF/00002.clpi"), new byte[0]);
		Files.write(bdmv.resolve("STREAM/00002.m2ts"), new byte[0]);
		return bdmv.getParent();
	}
}
