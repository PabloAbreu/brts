package org.brts.middle.descriptor;

final class LanguageCodeConstraints {

	static final String PATTERN = "[a-z]{3}";
	static final String MESSAGE = "must be a 3-letter ISO 639-2 language code";

	private LanguageCodeConstraints() {
	}

}