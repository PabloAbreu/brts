package org.brts.common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BrtsFileConfig {

	private final Map<String, Entry> resolvedProperties = new HashMap<>();

	private static final @Getter BrtsFileConfig instance = new BrtsFileConfig();

	private static final String DEFAULT_CONFIG_PATH = "/etc/default/brts.conf";

	private static final String USER_CONFIG_PATH = System.getProperty("user.home") + "/.brts/brts.conf";

	private static final String OVERRIDEN_CONFIG_PATH = System.getProperty("brts.conf");

	protected BrtsFileConfig() {
		// load properties from three sources, in order.
		// each loaded property overrides the previous ones, so that system properties
		// have the highest precedence.
		// 1/ default properties from /etc/default/brts.conf
		// 2/ user properties from ~/.brts/brts.conf
		// 3/ system properties passed via -Dbrts.conf on the command line
		// default
		loadFromFile(DEFAULT_CONFIG_PATH);
		loadFromFile(USER_CONFIG_PATH);
		loadFromFile(OVERRIDEN_CONFIG_PATH);
		resolveEnvironmentVariables();
		resolveSystemProperties();
		log.info("Final config properties: {}", resolvedProperties);
	}

	/**
	 * Resolves the value of a property by key, returning null if not found. Values are
	 * looked up in this order
	 * <ol>
	 * <li>System properties (e.g. passed via -D on the command line)</li>
	 * <li>environment variables (e.g. set in the shell or OS environment)</li>
	 * <li>Properties loaded from config files (in order of loading, with later files
	 * overriding earlier ones)</li>
	 * </ol>
	 * @param key the property key to look up
	 * @return the resolved property value, or null if not found in either loaded
	 * properties
	 */
	public String getProperty(String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Property key cannot be null or blank");
		}
		Entry entry = resolvedProperties.get(key);
		return entry != null ? entry.getValue() : null;
	}

	private static String getEnvVarName(String propertyKey) {
		return propertyKey.toUpperCase().replace('.', '_');
	}

	private void loadFromFile(String path) {
		try {
			try (var stream = new FileInputStream(path)) {
				Properties properties = new Properties();
				properties.load(stream);
				properties.stringPropertyNames().forEach(key -> addProperty(key, properties.getProperty(key), path));
				log.info("Loaded config from {}", path);
			}
			catch (FileNotFoundException e) {
				log.debug("No config found at {}. Error is " + path, e.getMessage());
			}
		}
		catch (Exception e) {
			// (error when closing the stream ?)
			log.trace("Error loading config from file: " + path, e);
		}
	}

	private void resolveEnvironmentVariables() {
		for (String key : resolvedProperties.keySet()) {
			String envVarName = getEnvVarName(key);
			String envValue = System.getenv(envVarName);
			if (envValue != null)
				addProperty(key, envValue, "env var " + envVarName);
		}
	}

	private void resolveSystemProperties() {
		for (String key : resolvedProperties.keySet()) {
			String systemValue = System.getProperty(key);
			if (systemValue != null)
				addProperty(key, systemValue, "system property");
		}
	}

	private void addProperty(String key, String value, String origin) {
		resolvedProperties.compute(key, (k, existing) -> {
			if (existing == null)
				return new Entry(k, value, origin);
			else {
				existing.update(value, origin);
				return existing;
			}
		});
	}

	@Getter
	@Setter
	@ToString
	public static class Entry {

		private final String key;

		private String value;

		private String origin;

		private boolean overridden = false;

		public Entry(String key, String value, String origin) {
			this.key = key;
			this.value = value;
			this.origin = origin;
		}

		public void update(String newValue, String newOrigin) {
			this.value = newValue;
			this.origin = newOrigin;
			this.overridden = true;
		}

	}

}
