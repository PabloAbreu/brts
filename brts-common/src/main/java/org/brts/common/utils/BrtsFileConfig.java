package org.brts.common.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BrtsFileConfig {

	private final Map<String, Entry> resolvedProperties = new HashMap<>();

	private static final @Getter BrtsFileConfig instance = new BrtsFileConfig();

	private static final String CONFIG_FILE_NAME = "brts.conf";

	private static final String CLASSPATH_RESOURCE = "/" + CONFIG_FILE_NAME;

	private static final String DEFAULT_CONFIG_PATH = "/etc/default/" + CONFIG_FILE_NAME;

	private final String USER_CONFIG_PATH = System.getProperty("user.home") + "/.brts/" + CONFIG_FILE_NAME;

	private final String OVERRIDEN_CONFIG_PATH = System.getProperty("brts.conf");

	protected BrtsFileConfig() {
		// load properties from several sources, in order.
		// each loaded property overrides the previous ones, so that system properties
		// have the highest precedence.
		// 0/ defaults from classpath embedded resource (e.g. src/main/resources/default-brts.conf)
		// 1/ default properties from /etc/default/brts.conf
		// 2/ user properties from ~/.brts/brts.conf
		// 3/ system properties passed via -Dbrts.conf on the command line
		// 4/ environment variables (e.g. set in the shell or OS environment)
		// 5/ system properties passed via -D on the command line (e.g. -Dmy.property=value)
		loadFromClasspathResource(CLASSPATH_RESOURCE);
		loadFromFile(DEFAULT_CONFIG_PATH);
		loadFromFile(USER_CONFIG_PATH);
		loadFromFile(OVERRIDEN_CONFIG_PATH);
		resolveEnvironmentVariables();
		resolveSystemProperties();
		log.debug("Final config properties: {}", resolvedProperties);
	}

	/**
	 * Resolves the value of a property by key, returning null if not found. Values are looked up in the order defined
	 * above.
	 *
	 * @param key the property key to look up
	 * @return the resolved property value, or null if not found in either loaded properties
	 */
	public String getProperty(String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Property key cannot be null or blank");
		}
		Entry entry = resolvedProperties.get(key);
		return entry != null ? entry.getValue() : null;
	}

	/**
	 * Returns a map of all properties whose keys start with the given prefix. The returned map contains the full
	 * property keys and their resolved values.
	 *
	 * @param prefix the prefix to filter property keys (e.g. "pgs.render." to get all pgs rendering-related properties)
	 * @return a map of property keys and values for all properties starting with the given prefix
	 */
	public Map<String, String> getPropertiesForPrefix(String prefix) {
		Map<String, String> result = new HashMap<>();
		for (Entry entry : resolvedProperties.values()) {
			if (entry.getKey().startsWith(prefix)) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	public <T> T fillPojo(T pojo) {
		// get prefix from class annotation, or default to empty string
		BrtsValue classAnnotation = pojo.getClass().getAnnotation(BrtsValue.class);
		String prefix = classAnnotation != null ? classAnnotation.value() : "";
		return fillPojo(pojo, prefix);
	}

	public <T> T fillPojo(T pojo, String prefix) {
		String effectivePrefix = prefix != null ? prefix : "";
		effectivePrefix = effectivePrefix.isBlank() ? ""
				: effectivePrefix.endsWith(".") ? effectivePrefix : effectivePrefix + ".";
		Map<String, String> props = getPropertiesForPrefix(effectivePrefix);
		Map<String, BrtsValue> allFields = ClassUtils.findAllFieldsHavingAnnotation(pojo, BrtsValue.class);
		log.debug("Filling POJO of type {} with config properties starting with '{}'", pojo.getClass().getName(),
				effectivePrefix);
		log.debug("Found fields {} ", allFields.keySet());
		// loop on fields in pojo (including superclass fields)
		for (String fieldName : allFields.keySet()) {
			BrtsValue annotation = allFields.get(fieldName);
			String propertyKey = effectivePrefix + (annotation.value().isBlank() ? fieldName : annotation.value());
			String propertyValue = props.get(propertyKey);
			if (propertyValue != null) {
				ClassUtils.setIfAbsent(pojo, fieldName, propertyValue);
			} else {
				log.debug("No config value found for property key '{}', skipping field '{}'", propertyKey, fieldName);
			}
		}
		return pojo;
	}

	private static String getEnvVarName(String propertyKey) {
		return propertyKey.toUpperCase().replace('.', '_');
	}

	private void loadFromClasspathResource(String resourcePath) {
		try (var stream = getClass().getResourceAsStream(resourcePath)) {
			if (stream == null) {
				log.debug("No config found in classpath at '{}'", resourcePath);
				return;
			}
			Properties properties = new Properties();
			properties.load(stream);
			properties.stringPropertyNames()
					.forEach(key -> addProperty(key, properties.getProperty(key), "classpath " + resourcePath));
			log.debug("Loaded config from classpath resource '{}'", resourcePath);
		} catch (Exception e) {
			log.debug("Error loading config from classpath resource '{}'", resourcePath, e);
		}
	}

	private void loadFromFile(String path) {
		if (path == null || path.isBlank())
			return;
		try {
			try (var stream = new FileInputStream(path)) {
				Properties properties = new Properties();
				properties.load(stream);
				properties.stringPropertyNames().forEach(key -> addProperty(key, properties.getProperty(key), path));
				log.debug("Loaded config from {}", path);
			} catch (FileNotFoundException e) {
				log.debug("No config found '{}'", e.getMessage());
			}
		} catch (Exception e) {
			// (error when closing the stream ?)
			log.debug("Error loading config from file: {}", path, e);
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

	public List<Entry> getAllProperties() {
		List<Entry> copy = new ArrayList<>(resolvedProperties.values());
		Collections.sort(copy, (a, b) -> a.getKey().compareTo(b.getKey()));
		return copy;
	}

	@Getter
	@ToString
	public static class Entry implements Cloneable {

		private final String key;

		private String value;

		private String origin;

		private boolean overridden = false;

		public Entry(String key, String value, String origin) {
			this.key = key;
			this.value = value;
			this.origin = origin;
		}

		private void update(String newValue, String newOrigin) {
			this.value = newValue;
			this.origin = newOrigin;
			this.overridden = true;
		}
	}
}
