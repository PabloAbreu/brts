package org.brts.doc.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.TypeMetadata;
import org.junit.jupiter.api.Test;

class CliMetadataParserTest {

	@Test
	void extractsRegisteredCommandsOptionsAndTypedInputGraph() throws Exception {
		Path root = Path.of("..").toAbsolutePath().normalize();
		CliMetadata metadata = new CliMetadataParser(List.of(root.resolve("brts-common/src/main/java"),
				root.resolve("brts-low-level/src/main/java"), root.resolve("brts-middle-level/src/main/java"),
				root.resolve("brts-high-level/src/main/java"), root.resolve("brts-cli/src/main/java"))).parse("test");

		assertThat(metadata.schemaVersion()).isEqualTo("1.0");
		assertThat(metadata.levels()).extracting("name").containsExactly("low", "mid", "high");
		assertThat(metadata.levels()).flatExtracting(level -> level.commands()).hasSize(40);
		assertThat(metadata.levels()).flatExtracting(level -> level.commands()).extracting("path").contains("mid build",
				"high build");

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