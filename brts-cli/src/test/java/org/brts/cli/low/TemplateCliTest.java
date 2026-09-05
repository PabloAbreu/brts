package org.brts.cli.low;

import org.brts.common.test.sampledata.RequiresSamples;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.cli.BrtsMain;
import org.brts.cli.LowLevelDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresSamples({ "PB/credits_template.svg.ftl", "PB/credits_tt31227572.json" })
class TemplateCliTest {

	@Test
	void renderTemplate_writesToOutputFile(@TempDir Path tempDir) throws Exception {
		Path templateFile = tempDir.resolve("sample.ftl");
		Files.writeString(templateFile, "<root><val>${name}</val></root>");

		Path dataFile = tempDir.resolve("data.json");
		Files.writeString(dataFile, "{\"name\": \"test-runner\"}");

		Path outputFile = tempDir.resolve("out.xml");

		new LowLevelDispatcher().dispatch(new String[] { "render-template", "--template", templateFile.toString(),
				"--data", dataFile.toString(), "--output", outputFile.toString() });

		assertThat(Files.exists(outputFile)).isTrue();
		assertThat(Files.readString(outputFile)).isEqualTo("<root><val>test-runner</val></root>");
	}

	@Test
	void renderTemplate_withInlineJson_printsToStdout(@TempDir Path tempDir) throws Exception {
		Path templateFile = tempDir.resolve("sample.txt");
		Files.writeString(templateFile, "Greeting: ${msg}");

		PrintStream originalOut = System.out;
		ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(outCapture));
			new LowLevelDispatcher().dispatch(new String[] { "render-template", "--template", templateFile.toString(),
					"--data", "{\"msg\": \"hello world\"}" });
		} finally {
			System.setOut(originalOut);
		}

		assertThat(outCapture.toString().trim()).isEqualTo("Greeting: hello world");
	}

	@Test
	void renderTemplate_viaBrtsMainDispatcher(@TempDir Path tempDir) throws Exception {
		Path templateFile = tempDir.resolve("main_test.ftl");
		Files.writeString(templateFile, "ID=${id}");

		Path outputFile = tempDir.resolve("main_out.txt");

		BrtsMain.main(new String[] { "low", "render-template", "--template", templateFile.toString(), "--data",
				"{\"id\": 999}", "--output", outputFile.toString() });

		assertThat(Files.exists(outputFile)).isTrue();
		assertThat(Files.readString(outputFile)).isEqualTo("ID=999");
	}

	@Test
	void renderTemplate_creditsSampleIntegration(@TempDir Path tempDir) throws Exception {
		Path outputFile = tempDir.resolve("credits.svg");
		String samplesDir = System.getProperty("test.samples.dir", "samples");
		Path templateFile = Path.of(samplesDir, "PB", "credits_template.svg.ftl");
		Path dataFile = Path.of(samplesDir, "PB", "credits_tt31227572.json");

		BrtsMain.main(new String[] { "low", "render-template", "--template", templateFile.toString(), "--data",
				dataFile.toString(), "--output", outputFile.toString() });

		assertThat(Files.exists(outputFile)).isTrue();
		String content = Files.readString(outputFile);
		assertThat(content).contains("<svg");
		assertThat(content).contains("Elle Fanning");
		assertThat(content).contains("Thia / Tessa");
		assertThat(content).contains("Dan Trachtenberg");
		assertThat(content).contains("</svg>");
	}
}
