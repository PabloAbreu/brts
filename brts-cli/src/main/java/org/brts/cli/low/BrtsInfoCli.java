/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/low/BrtsInfoCli.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.brts.cli.FeatureRunner;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.utils.BrtsFileConfig;
import org.kohsuke.args4j.Option;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

/**
 * CLI for the {@code brts-info} command.
 */
public class BrtsInfoCli {
	public enum OutputFormat {
		JSON, TEXT
	}

	public static class InfoOptions extends org.brts.cli.BaseOptions {
		@Option(name = "--all", usage = "Include all system properties and environment variables in the output")
		boolean includeAll;

		@Option(name = "--format", usage = "Output format: JSON (default) or TEXT (human-readable tables)")
		OutputFormat format = OutputFormat.JSON;
	}

	public static class Info extends FeatureRunner<InfoOptions> {
		@Override
		public String getCommandName() {
			return "brts-info";
		}

		@Override
		public String getDescription() {
			return "Display information about the BRTS toolkit, such as version and config properties.";
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		protected void execute(InfoOptions opts) throws Exception {
			BrtsFileConfig config = BrtsFileConfig.getInstance();
			BrtsInfo info = new BrtsInfo();
			Package pkg = BrtsInfoCli.class.getPackage();
			info.setAppName(pkg.getImplementationTitle());
			info.setVersion(pkg.getImplementationVersion());
			info.setConfigProperties(config.getAllProperties());
			if (opts.includeAll) {
				info.setSystemProperties(new TreeMap<>((Map) System.getProperties())); // sort system properties by key
				info.setEnvironmentVariables(new TreeMap<>(System.getenv())); // sort environment variables by key
			}
			switch (opts.format) {
			case JSON -> {
				ObjectMapper mapper = JsonMapperFactory.get();
				System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info));
			}
			case TEXT -> System.out.print(renderText(info));
			}
		}
	}

	static String renderText(BrtsInfo info) {
		Map<String, List<String[]>> sections = new LinkedHashMap<>();
		List<String[]> configRows = new ArrayList<>();
		configRows.add(new String[] { "Key", "Value", "Origin", "Overridden" });
		if (info.getConfigProperties() != null) {
			for (BrtsFileConfig.Entry entry : info.getConfigProperties()) {
				configRows.add(new String[] { entry.getKey(), entry.getValue(), entry.getOrigin(),
						Boolean.toString(entry.isOverridden()) });
			}
		}
		sections.put("Configuration Properties", configRows);
		addPropertySection(sections, "System Properties", info.getSystemProperties());
		addPropertySection(sections, "Environment Variables", info.getEnvironmentVariables());
		int[] widths = new int[4];
		for (List<String[]> rows : sections.values()) {
			for (String[] row : rows) {
				for (int column = 0; column < row.length; column++) {
					row[column] = displayCell(row[column]);
					widths[column] = Math.max(widths[column], row[column].length());
				}
			}
		}
		StringBuilder output = new StringBuilder();
		output.append(displayCell(info.getAppName() == null ? "BRTS" : info.getAppName())).append('\n');
		output.append("Version: ").append(displayCell(info.getVersion() == null ? "unknown" : info.getVersion()))
				.append('\n');
		for (Map.Entry<String, List<String[]>> section : sections.entrySet()) {
			output.append('\n').append(section.getKey()).append('\n');
			for (String[] row : section.getValue()) {
				for (int column = 0; column < row.length; column++) {
					output.append(row[column]);
					if (column < row.length - 1) {
						output.append(" ".repeat(widths[column] - row[column].length())).append("  ");
					}
				}
				output.append('\n');
			}
		}
		return output.toString();
	}

	private static void addPropertySection(Map<String, List<String[]>> sections, String title,
			Map<String, String> properties) {
		if (properties != null) {
			List<String[]> rows = new ArrayList<>();
			rows.add(new String[] { "Key", "Value" });
			new TreeMap<>(properties).forEach((key, value) -> rows.add(new String[] { key, value }));
			sections.put(title, rows);
		}
	}

	private static String displayCell(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
			case '\\' -> result.append("\\\\");
			case '\n' -> result.append("\\n");
			case '\r' -> result.append("\\r");
			case '\t' -> result.append("\\t");
			default -> {
				if (Character.isISOControl(character)) {
					result.append(String.format("\\u%04x", (int) character));
				} else {
					result.append(character);
				}
			}
			}
		}
		return result.toString();
	}

	@Getter
	@Setter
	public static class BrtsInfo {
		private String appName;
		private String version;
		private List<BrtsFileConfig.Entry> configProperties;
		private Map<String, String> systemProperties;
		private Map<String, String> environmentVariables;
	}
}
