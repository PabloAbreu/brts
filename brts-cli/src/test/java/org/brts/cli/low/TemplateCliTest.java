package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/test/java/org/brts/cli/low/TemplateCliTest.java' is part of BRTS.
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

		LowLevelDispatcher.main(new String[] { "render-template", "--no-banner", "--template", templateFile.toString(),
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
			LowLevelDispatcher.main(new String[] { "render-template", "--no-banner", "--template",
					templateFile.toString(), "--data", "{\"msg\": \"hello world\"}" });
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

		BrtsMain.main(new String[] { "low", "render-template", "--no-banner", "--template", templateFile.toString(),
				"--data", "{\"id\": 999}", "--output", outputFile.toString() });

		assertThat(Files.exists(outputFile)).isTrue();
		assertThat(Files.readString(outputFile)).isEqualTo("ID=999");
	}

	@Test
	void renderTemplate_creditsSampleIntegration(@TempDir Path tempDir) throws Exception {
		Path outputFile = tempDir.resolve("credits.svg");
		String samplesDir = System.getProperty("test.samples.dir", "samples");
		Path templateFile = Path.of(samplesDir, "PB", "credits_template.svg.ftl");
		Path dataFile = Path.of(samplesDir, "PB", "credits_tt31227572.json");

		BrtsMain.main(new String[] { "low", "render-template", "--no-banner", "--template", templateFile.toString(),
				"--data", dataFile.toString(), "--output", outputFile.toString() });

		assertThat(Files.exists(outputFile)).isTrue();
		String content = Files.readString(outputFile);
		assertThat(content).contains("<svg");
		assertThat(content).contains("Elle Fanning");
		assertThat(content).contains("Thia / Tessa");
		assertThat(content).contains("Dan Trachtenberg");
		assertThat(content).contains("</svg>");
	}
}
