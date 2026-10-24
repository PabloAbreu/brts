/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-doc-parser/src/test/java/org/brts/doc/parser/CliMetadataParserTest.java' is part of BRTS.
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

package org.brts.doc.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.OptionMetadata;
import org.brts.doc.metadata.TypeMetadata;
import org.junit.jupiter.api.Test;

class CliMetadataParserTest {

	// update this when adding a feature
	private static final int TOTAL_NUMBER_OF_FEATURES = 46;

	@Test
	void extractsRegisteredCommandsOptionsAndTypedInputGraph() throws Exception {
		Path root = Path.of("..").toAbsolutePath().normalize();
		CliMetadata metadata = new CliMetadataParser(List.of(root.resolve("brts-common/src/main/java"),
				root.resolve("brts-low-level/src/main/java"), root.resolve("brts-middle-level/src/main/java"),
				root.resolve("brts-high-level/src/main/java"), root.resolve("brts-cli/src/main/java"))).parse("test");

		assertThat(metadata.schemaVersion()).isEqualTo("1.1");
		assertThat(metadata.levels()).extracting("name").containsExactly("low", "mid", "high");

		assertThat(metadata.levels()).flatExtracting(level -> level.commands()).hasSize(TOTAL_NUMBER_OF_FEATURES);
		assertThat(metadata.levels()).flatExtracting(level -> level.commands()).extracting("path").contains("mid build",
				"high build", "high title-menu-simulate", "low video-convert");

		CommandMetadata convertVideo = metadata.levels().stream().flatMap(level -> level.commands().stream())
				.filter(command -> command.path().equals("low video-convert")).findFirst().orElseThrow();
		assertThat(convertVideo.options()).extracting("name").containsExactly("--error-details", "--no-banner",
				"--input", "--output", "--codec", "--engine", "--executable", "--width", "--height", "--frame-rate",
				"--source-frame-rate", "--bitrate-kbps", "--rate-control", "--engine-option", "--overwrite",
				"--timeout-seconds");
		assertThat(convertVideo.options()).filteredOn(option -> option.name().equals("--engine-option")).singleElement()
				.satisfies(option -> {
					assertThat(option.javaType()).isEqualTo("java.util.List<java.lang.String>");
					assertThat(option.description()).contains("Repeat", "converter-approved");
				});
		assertThat(convertVideo.options()).filteredOn(option -> option.name().equals("--input")).singleElement()
				.satisfies(option -> assertThat(option.required()).isTrue());
		assertThat(convertVideo.options()).filteredOn(option -> option.name().equals("--timeout-seconds"))
				.singleElement().satisfies(option -> assertThat(option.javaType()).isEqualTo("java.lang.Long"));
		assertThat(convertVideo.options()).filteredOn(option -> option.name().equals("--codec")).singleElement()
				.satisfies(option -> assertThat(option.enumValues()).isEmpty());
		List<OptionMetadata> outputTypeOptions = metadata.levels().stream().flatMap(level -> level.commands().stream())
				.flatMap(command -> command.options().stream()).filter(option -> option.name().equals("--output-type"))
				.toList();
		assertThat(outputTypeOptions).isNotEmpty()
				.allSatisfy(option -> assertThat(option.enumValues()).containsExactly("BASH", "JSON", "IMMEDIATE"));

		CommandMetadata middleBuild = metadata.levels().stream().flatMap(level -> level.commands().stream())
				.filter(command -> command.path().equals("mid build")).findFirst().orElseThrow();
		assertThat(middleBuild.options()).filteredOn(option -> option.name().equals("--error-details")).singleElement()
				.satisfies(option -> {
					assertThat(option.hidden()).isTrue();
					assertThat(option.inherited()).isTrue();
				});
		assertThat(middleBuild.options()).filteredOn(option -> option.name().equals("--descriptor")).singleElement()
				.satisfies(option -> assertThat(option.inputTypeRef())
						.isEqualTo("org.brts.middle.descriptor.DiscDescriptor"));

		TypeMetadata titleMenu = metadata.types().get("org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor");
		assertThat(titleMenu.description()).contains("Root descriptor for the title menu feature");
		assertThat(titleMenu.fields()).filteredOn(field -> field.name().equals("screenWidth")).singleElement()
				.satisfies(field -> assertThat(field.description()).contains("Screen width in pixels"));
		assertThat(metadata.types().get("org.brts.middle.menu.descriptor.BackgroundMediaDescriptor").subtypes())
				.containsEntry("MKV", "org.brts.middle.menu.descriptor.MkvBackgroundMedia");
	}
}
