package org.brts.common.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRendererTest {

	@Test
	void renderTemplateString_withMap() {
		String template = "Hello, ${name}! You have ${count} messages.";
		Map<String, Object> data = Map.of("name", "World", "count", 42);

		String result = TemplateRenderer.render(template, data);

		assertThat(result).isEqualTo("Hello, World! You have 42 messages.");
	}

	@Test
	void renderTemplateString_numberFormatIsComputer() {
		String template = "ID: ${id}";
		Map<String, Object> data = Map.of("id", 1234567);

		String result = TemplateRenderer.render(template, data);

		// Must not have comma separators (e.g. 1,234,567)
		assertThat(result).isEqualTo("ID: 1234567");
	}

	@Test
	void renderTemplate_fromFile(@TempDir Path tempDir) throws Exception {
		Path templateFile = tempDir.resolve("greeting.ftl");
		Files.writeString(templateFile, "Welcome, ${user.firstName} ${user.lastName}!");

		Map<String, Object> data = Map.of("user", Map.of("firstName", "John", "lastName", "Doe"));

		String result = TemplateRenderer.render(templateFile, data);

		assertThat(result).isEqualTo("Welcome, John Doe!");
	}

	@Test
	void renderWithJsonString() {
		String template = "<#list items as item>[${item}]</#list>";
		String json = "{\"items\": [\"A\", \"B\", \"C\"]}";

		String result = TemplateRenderer.getInstance().renderTemplateStringWithJsonString(template, json);

		assertThat(result).isEqualTo("[A][B][C]");
	}

	@Test
	void renderWithJsonFile_andOutputToFile(@TempDir Path tempDir) throws Exception {
		Path templateFile = tempDir.resolve("template.txt");
		Files.writeString(templateFile, "Title: ${title}, Total: ${total}");

		Path jsonFile = tempDir.resolve("data.json");
		Files.writeString(jsonFile, "{\"title\": \"BRTS Suite\", \"total\": 100}");

		Path outputFile = tempDir.resolve("output.txt");

		TemplateRenderer.renderJsonToFile(templateFile, jsonFile, outputFile);

		assertThat(Files.readString(outputFile)).isEqualTo("Title: BRTS Suite, Total: 100");
	}

	@Test
	void renderCreditsTemplate_withRealSampleData() {
		Path samplesRoot = org.brts.common.test.sampledata.Samples.root();
		Path templatePath = samplesRoot.resolve("PB/credits_template.svg.ftl");
		Path jsonPath = samplesRoot.resolve("PB/credits_tt31227572.json");

		String svg = TemplateRenderer.renderJson(templatePath, jsonPath);

		assertThat(svg).isNotBlank();
		assertThat(svg).contains("<svg");
		assertThat(svg).contains("CLOSING CREDITS");
		assertThat(svg).contains("Elle Fanning");
		assertThat(svg).contains("Thia / Tessa");
		assertThat(svg).contains("Dan Trachtenberg");
		assertThat(svg).contains("Director");
		assertThat(svg).contains("</svg>");
	}

	@Test
	void renderWithMissingFile_throwsException(@TempDir Path tempDir) {
		Path nonExistentTemplate = tempDir.resolve("missing.ftl");
		Path nonExistentJson = tempDir.resolve("missing.json");

		assertThatThrownBy(() -> TemplateRenderer.renderJson(nonExistentTemplate, nonExistentJson))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
