package org.brts.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class BrtsFileConfigTest {

	private static final String TRUE_KEY = "brts.test.boolean.true";
	private static final String YES_KEY = "brts.test.boolean.yes";
	private static final String ONE_KEY = "brts.test.boolean.one";
	private static final String FALSE_KEY = "brts.test.boolean.false";
	private static final String ZERO_KEY = "brts.test.boolean.zero";
	private static final String NO_KEY = "brts.test.boolean.no";
	private static final String BLANK_KEY = "brts.test.boolean.blank";
	private static final String INVALID_KEY = "brts.test.boolean.invalid";
	private static final BrtsFileConfig CONFIG;

	static {
		System.setProperty(TRUE_KEY, "TrUe");
		System.setProperty(YES_KEY, " YeS ");
		System.setProperty(ONE_KEY, "1");
		System.setProperty(FALSE_KEY, "nO");
		System.setProperty(ZERO_KEY, "0");
		System.setProperty(NO_KEY, "FALSE");
		System.setProperty(BLANK_KEY, " ");
		System.setProperty(INVALID_KEY, "maybe");
		// force re-resolution in case another test class already resolved the singleton in this fork
		BrtsFileConfig.resetForTests();
		BrtsFileConfig.registerForTests(
				List.of(TRUE_KEY, YES_KEY, ONE_KEY, FALSE_KEY, ZERO_KEY, NO_KEY, BLANK_KEY, INVALID_KEY));
		CONFIG = BrtsFileConfig.getInstance();
		log.debug("Registered test properties: {}", CONFIG.getAllProperties());
	}

	@AfterAll
	static void clearTestProperties() {
		System.clearProperty(TRUE_KEY);
		System.clearProperty(YES_KEY);
		System.clearProperty(ONE_KEY);
		System.clearProperty(FALSE_KEY);
		System.clearProperty(ZERO_KEY);
		System.clearProperty(NO_KEY);
		System.clearProperty(BLANK_KEY);
		System.clearProperty(INVALID_KEY);
		// avoid leaking this test's resolved singleton into subsequently run test classes
		BrtsFileConfig.resetForTests();
	}

	@Test
	void parsesCaseInsensitiveTruthyAliases() {
		assertThat(CONFIG.parseBooleanProperty(TRUE_KEY, false)).isTrue();
		assertThat(CONFIG.parseBooleanProperty(YES_KEY, false)).isTrue();
		assertThat(CONFIG.parseBooleanProperty(ONE_KEY, false)).isTrue();
	}

	@Test
	void parsesFalseAliases() {
		assertThat(CONFIG.parseBooleanProperty(FALSE_KEY, true)).isFalse();
		assertThat(CONFIG.parseBooleanProperty(ZERO_KEY, true)).isFalse();
		assertThat(CONFIG.parseBooleanProperty(NO_KEY, true)).isFalse();
	}

	@Test
	void returnsFallbackForBlankMissingAndInvalidValues() {
		assertThat(CONFIG.parseBooleanProperty(BLANK_KEY, true)).isTrue();
		assertThat(CONFIG.parseBooleanProperty(BLANK_KEY, false)).isFalse();
		assertThat(CONFIG.parseBooleanProperty("brts.test.boolean.missing", true)).isTrue();
		assertThat(CONFIG.parseBooleanProperty(INVALID_KEY, false)).isFalse();
	}
}
