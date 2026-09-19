package org.brts.doc.generator;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-generator/src/test/java/org/brts/doc/generator/MarkdownDocumentationGeneratorTest.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.doc.metadata.AnnotationMetadata;
import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.FieldMetadata;
import org.brts.doc.metadata.LevelMetadata;
import org.brts.doc.metadata.OptionMetadata;
import org.brts.doc.metadata.TypeMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownDocumentationGeneratorTest {

	@TempDir
	Path output;

	@Test
	void generatesNavigableCommandAndTypePages() throws Exception {
		String payloadType = "org.brts.example.BuildDescriptor";
		OptionMetadata option = new OptionMetadata("--descriptor", List.of("-d"), "Input descriptor", payloadType, true,
				false, false, List.of(), List.of(), false,
				List.of(new AnnotationMetadata("org.kohsuke.args4j.Option", Map.of("name", "--descriptor")),
						new AnnotationMetadata("jakarta.validation.Valid", Map.of())),
				payloadType);
		CommandMetadata command = new CommandMetadata("mid", "build", "mid build", "Build a disc", "Runner", "Options",
				List.of(option));
		Map<String, Object> patternAttributes = new LinkedHashMap<>();
		patternAttributes.put("groups", List.of());
		patternAttributes.put("payload", List.of());
		patternAttributes.put("regexp", "[A-Za-z0-9 _]+");
		FieldMetadata field = new FieldMetadata("name", "discName", "java.lang.String", "Disc name", true,
				List.of(new AnnotationMetadata("jakarta.validation.constraints.Pattern", patternAttributes)), null,
				List.of());
		TypeMetadata type = new TypeMetadata(payloadType, "object", "Build input", null, List.of(), List.of(), null,
				Map.of(), List.of(field));
		Map<String, TypeMetadata> types = new LinkedHashMap<>();
		types.put(payloadType, type);
		CliMetadata metadata = new CliMetadata("1.0", "1.2.3", List.of(new LevelMetadata("mid", List.of(command))),
				types, List.of());

		new MarkdownDocumentationGenerator().generate(metadata, output);

		assertThat(output.resolve("index.md")).content().contains("BRTS CLI Reference", "1.2.3",
				"commands/mid/index.md");
		assertThat(output.resolve("commands/mid/build.md")).content().contains("# `mid build`", "--descriptor",
				"../../types/org-brts-example-builddescriptor.md", "  - `@Valid`");
		assertThat(output.resolve("commands/mid/build.md")).content().doesNotContain("org.kohsuke.args4j");
		assertThat(output.resolve("types/org-brts-example-builddescriptor.md")).content().contains("Build input",
				"`discName`", "Disc name", "  - `@Pattern` - regexp: `[A-Za-z0-9 _]+`");
		assertThat(output.resolve("types/org-brts-example-builddescriptor.md")).content().doesNotContain("payload",
				"groups");
		assertThat(Files.list(output.resolve("commands/mid"))).hasSize(2);
	}
}
