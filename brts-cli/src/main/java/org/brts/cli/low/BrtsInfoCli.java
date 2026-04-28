package org.brts.cli.low;

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
	public static class InfoOptions {
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
