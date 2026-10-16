/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/BrtsInfoCliTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.brts.cli.low.BrtsInfoCli.BrtsInfo;
import org.brts.cli.low.BrtsInfoCli.Info;
import org.brts.cli.low.BrtsInfoCli.InfoOptions;
import org.brts.cli.low.BrtsInfoCli.OutputFormat;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.utils.BrtsFileConfig.Entry;
import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

class BrtsInfoCliTest {
	private static InfoOptions options(String... arguments) throws CmdLineException {
		InfoOptions options = new InfoOptions();
		new CmdLineParser(options).parseArgument(arguments);
		return options;
	}

	private static String execute(String... arguments) throws Exception {
		PrintStream original = System.out;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setOut(stream);
			new Info().execute(options(arguments));
		} finally {
			System.setOut(original);
		}
		return output.toString(StandardCharsets.UTF_8);
	}

	@Test
	void parsesFormatsAndRetainsJsonDefault() throws Exception {
		assertThat(options().format).isEqualTo(OutputFormat.JSON);
		assertThat(options("--format", "JSON").format).isEqualTo(OutputFormat.JSON);
		InfoOptions text = options("--format", "TEXT", "--all");
		assertThat(text.format).isEqualTo(OutputFormat.TEXT);
		assertThat(text.includeAll).isTrue();
		assertThatThrownBy(() -> options("--format", "XML")).isInstanceOf(CmdLineException.class);
	}

	@Test
	void defaultAndExplicitJsonKeepExistingSchema() throws Exception {
		String defaultOutput = execute();
		assertThat(execute("--format", "JSON")).isEqualTo(defaultOutput);
		var json = JsonMapperFactory.get().readTree(defaultOutput);
		assertThat(json.has("configProperties")).isTrue();
		assertThat(json.get("configProperties").isArray()).isTrue();
		assertThat(defaultOutput).doesNotContain("Configuration Properties");
	}

	@Test
	void routesTextAndIncludesOptionalSectionsOnlyWithAll() throws Exception {
		assertThat(execute("--format", "TEXT")).contains("Version:", "Configuration Properties")
				.doesNotContain("System Properties\n", "Environment Variables\n");
		assertThat(execute("--format", "TEXT", "--all")).contains("System Properties\n", "Environment Variables\n");
	}

	@Test
	void measuresAllRowsBeforeRenderingFourColumns() {
		BrtsInfo info = new BrtsInfo();
		info.setAppName("Blu-ray Tools Suite");
		info.setVersion("1.2.3");
		Entry overridden = new Entry("longest.property.key", "the longest configuration value",
				"the longest configuration origin") {
			@Override
			public boolean isOverridden() {
				return true;
			}
		};
		info.setConfigProperties(List.of(new Entry("a", "b", "c"), overridden));
		List<String> lines = BrtsInfoCli.renderText(info).lines().toList();
		assertThat(lines.subList(0, 4)).containsExactly("Blu-ray Tools Suite", "Version: 1.2.3", "",
				"Configuration Properties");
		String header = lines.get(4);
		String first = lines.get(5);
		String last = lines.get(6);
		assertThat(header.indexOf("Value")).isEqualTo("longest.property.key".length() + 2);
		assertThat(first.indexOf("b")).isEqualTo(header.indexOf("Value"));
		assertThat(last.indexOf("the longest configuration value")).isEqualTo(header.indexOf("Value"));
		assertThat(first.indexOf("c")).isEqualTo(header.indexOf("Origin"));
		assertThat(last.indexOf("the longest configuration origin")).isEqualTo(header.indexOf("Origin"));
		assertThat(first.indexOf("false")).isEqualTo(header.indexOf("Overridden"));
		assertThat(last.indexOf("true")).isEqualTo(header.indexOf("Overridden"));
	}

	@Test
	void measuresOptionalSectionsInAdvanceAndUsesOnlyTwoColumns() {
		BrtsInfo info = new BrtsInfo();
		info.setConfigProperties(List.of(new Entry("a", "b", "c")));
		info.setSystemProperties(Map.of("z", "last", "a", "first"));
		String key = "THE_LONGEST_ENVIRONMENT_KEY";
		String value = "the longest value appears in the final section";
		info.setEnvironmentVariables(Map.of(key, value));
		List<String> lines = BrtsInfoCli.renderText(info).lines().toList();
		String header = lines.get(4);
		assertThat(header.indexOf("Value")).isEqualTo(key.length() + 2);
		assertThat(header.indexOf("Origin")).isEqualTo(key.length() + 2 + value.length() + 2);
		int system = lines.indexOf("System Properties");
		int environment = lines.indexOf("Environment Variables");
		assertThat(lines.get(system + 1)).isEqualTo("Key" + " ".repeat(key.length() - 3 + 2) + "Value");
		assertThat(lines.get(system + 2).strip()).startsWith("a").endsWith("first");
		assertThat(lines.get(system + 3).strip()).startsWith("z").endsWith("last");
		assertThat(lines.get(environment + 2)).isEqualTo(key + "  " + value);
	}

	@Test
	void emptySectionsUseHeadingWidthsAndMissingMetadataFallbacks() {
		BrtsInfo info = new BrtsInfo();
		info.setConfigProperties(List.of());
		info.setSystemProperties(Map.of());
		info.setEnvironmentVariables(Map.of());
		assertThat(BrtsInfoCli.renderText(info)).isEqualTo("BRTS\nVersion: unknown\n\nConfiguration Properties\n"
				+ "Key  Value  Origin  Overridden\n\nSystem Properties\nKey  Value\n\nEnvironment Variables\nKey  Value\n");
	}

	@Test
	void escapesControlsBeforeMeasuringAndDoesNotAlterSourceData() {
		BrtsInfo info = new BrtsInfo();
		Entry entry = new Entry("a", "line\n\r\t\\\u001b", null);
		info.setConfigProperties(List.of(entry, new Entry("b", null, "source")));
		List<String> lines = BrtsInfoCli.renderText(info).lines().toList();
		String escaped = "line\\n\\r\\t\\\\\\u001b";
		assertThat(lines).hasSize(7);
		assertThat(lines.get(5)).contains(escaped);
		assertThat(lines.get(4).indexOf("Origin")).isEqualTo(5 + escaped.length() + 2);
		assertThat(lines.get(6).indexOf("source")).isEqualTo(lines.get(4).indexOf("Origin"));
		assertThat(entry.getValue()).isEqualTo("line\n\r\t\\\u001b");
	}
}
