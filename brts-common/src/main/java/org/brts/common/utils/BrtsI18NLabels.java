package org.brts.common.utils;

import java.util.Locale;



public class BrtsI18NLabels {
	public static final String MENU_TO_AUDIO = "i18n.output.menu.to.audio";
	public static final String MENU_TO_SUBTITLES = "i18n.output.menu.to.subtitles";
	public static final String MENU_EXIT = "i18n.output.menu.exit";
	public static final String MENU_BACK = "i18n.output.menu.back";
	public static final String MENU_SETTINGS = "i18n.output.menu.settings";
	public static final String MENU_LANGUAGE_PREFIX = "i18n.output.menu.languages.";

	public static String getLabel(String language, String key) {
		return BrtsI18N.getInstance().getMessage(language, key);
	}

	public static String getLabel(String key) {
		return BrtsI18N.getInstance().getMessage(BrtsI18N.getInstance().getDefaultLanguage(), key);
	}

	public static String getLanguageName(String languageCode, String fallback) {
		String key = MENU_LANGUAGE_PREFIX + languageCode.toUpperCase(Locale.ROOT);
		BrtsI18N i18n = BrtsI18N.getInstance();
		return i18n.getMessage(i18n.getDefaultLanguage(), key, fallback);
	}
}
