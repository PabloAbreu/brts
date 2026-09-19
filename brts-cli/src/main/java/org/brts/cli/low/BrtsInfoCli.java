package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/BrtsInfoCli.java' is part of BRTS.
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
 * CLI for the {@code clpi-regen} command.
 */
public class BrtsInfoCli {
	public static class InfoOptions extends org.brts.cli.BaseOptions {
		@Option(name = "--all", usage = "Include all system properties and environment variables in the output")
		boolean includeAll;
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
			ObjectMapper mapper = JsonMapperFactory.get();
			String configJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
			System.out.println(configJson);
		}
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
