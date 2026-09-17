package org.brts.doc.generator;

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
				List.of(new AnnotationMetadata("jakarta.validation.Valid", Map.of())), payloadType);
		CommandMetadata command = new CommandMetadata("mid", "build", "mid build", "Build a disc", "Runner", "Options",
				List.of(option));
		FieldMetadata field = new FieldMetadata("name", "discName", "java.lang.String", "Disc name", true, List.of(),
				null, List.of());
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
				"../../types/org-brts-example-builddescriptor.md");
		assertThat(output.resolve("types/org-brts-example-builddescriptor.md")).content().contains("Build input",
				"`discName`", "Disc name");
		assertThat(Files.list(output.resolve("commands/mid"))).hasSize(2);
	}
}