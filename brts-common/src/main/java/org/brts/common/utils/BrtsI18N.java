package org.brts.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and resolves internationalized messages for BRTS.
 *
 * Not really I18N in the java resource bundle sense, as we do not support things like "en_US" , just eng/fra/jpn etc.
 * as typical in Blu-rays.
 *
 * Three-letter language codes are used, matching the ISO 639-2 standard.
 *
 * "und" (undefined) is used as the default language code when no specific language is set or detected. Labels for "und"
 * will be the English ones, except when overridden by the "i18n.default.language" BrtsFileConfig property.
 *
 */
@Slf4j
public class BrtsI18N {

	private static final @Getter BrtsI18N instance = new BrtsI18N();
	private static final String DEFAULT_LANGUAGE_KEY = "i18n.default.language";
	private static final String UNDEFINED_LANGUAGE_CODE = "und";
	private static final String ENGLISH_LANGUAGE_CODE = "eng";
	private static final String I18N_CLASSPATH_FOLDER = "i18n/";
	private static final String PROPERTIES_EXT = ".properties";
	private static final int PROPERTIES_FILE_NAME_LENGTH = 14; // e.g. eng.properties
	private final Map<String, Map<String, String>> labelsByLanguage = new TreeMap<>();

	protected BrtsI18N() {
		// first load all properties from the classpath in i18n/{lng}.properties, where
		// {lng} is the language code (e.g. eng, fra, jpn)
		// then override with any properties defined ~/.brts/i18n/{lng}.properties

		// classpath properties
		// iterate on all files in the classpath under folder i18n/ that match the
		// pattern {lng}.properties, load them and store the labels in a map
		try {
			ClasspathUtils.enumerate(I18N_CLASSPATH_FOLDER, name -> name.endsWith(PROPERTIES_EXT)).forEach(name -> {
				String languageCode = name.substring(0, name.length() - PROPERTIES_EXT.length());
				log.debug("Loading classpath i18n properties for language code '{}' and name '{}'", languageCode, name);
				try (InputStream is = ClasspathUtils.getResourceAsStream(I18N_CLASSPATH_FOLDER + name)) {
					loadLabelsForLanguage(languageCode, is);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});

			// user properties
			// look for files in ~/.brts/i18n/{lng}.properties, load them and override any
			// existing labels for the same language code
			String userConfigDir = System.getProperty("user.home") + "/.brts/" + I18N_CLASSPATH_FOLDER;
			File userConfigDirFile = new File(userConfigDir);
			if (userConfigDirFile.exists() && userConfigDirFile.isDirectory()) {
				File[] files = userConfigDirFile.listFiles(f -> f.isFile() && f.getName().endsWith(PROPERTIES_EXT)
						&& f.getName().length() == PROPERTIES_FILE_NAME_LENGTH);
				for (File f : files) {
					String languageCode = f.getName().substring(0, f.getName().length() - PROPERTIES_EXT.length());
					log.debug("Loading user i18n properties for language code '{}' and file '{}'", languageCode,
							f.getName());
					try (InputStream is = new FileInputStream(f)) {
						loadLabelsForLanguage(languageCode, is);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void loadLabelsForLanguage(String languageCode, InputStream propertiesStream) throws IOException {
		// load labels for the given language code from classpath and user config, and
		// store them in the labelsByLanguage map
		// merging over any existing map values for the same language code, so that user
		// config overrides classpath config
		final Map<String, String> labels = labelsByLanguage.computeIfAbsent(languageCode, k -> new TreeMap<>());
		log.info("Loading {} labels for language code '{}'", labels.isEmpty() ? "default" : "additional", languageCode);
		Properties p = new Properties();
		p.load(propertiesStream);
		p.forEach((key, value) -> labels.put((String) key, (String) value));
	}

	public String getDefaultLanguage() {
		return BrtsFileConfig.getInstance().getProperty(DEFAULT_LANGUAGE_KEY);
	}

	public String getMessage(String languageCode, String key) {
		// return the localized message for the given key, based on the current language
		// setting
		// this is a placeholder implementation; you would replace this with actual
		// message loading logic
		if (languageCode == null || languageCode.isBlank() || languageCode.equals(UNDEFINED_LANGUAGE_CODE)) {
			languageCode = getDefaultLanguage();
		}
		Map<String, String> labels = labelsByLanguage.get(languageCode);
		// if no labels found for the requested language, fall back to configured
		// default language
		if (labels == null)
			labels = labelsByLanguage.get(languageCode = getDefaultLanguage());

		// if configured default language points to nowhere, fall back to English
		if (labels == null)
			labels = labelsByLanguage.get(languageCode = ENGLISH_LANGUAGE_CODE);

		// this one should never happen
		if (labels == null)
			return " -- missing labels -- ";
		// uncomplete translation ?
		// when not found we also could have returned the english label instead of the
		// message
		// but that would have made it harder to detect missing translations, so we
		// return a clear message instead
		return labels.getOrDefault(key, " - missing label for key " + key + " and language " + languageCode + " - ");
	}
}
