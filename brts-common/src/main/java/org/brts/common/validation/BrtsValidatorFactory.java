package org.brts.common.validation;

import org.hibernate.validator.HibernateValidator;

import java.util.Locale;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Lazily-initialised, process-wide {@link Validator} provider.
 * <p>
 * Mirrors the {@code JsonMapperFactory} convention: a single shared, thread-safe instance is built on first use.
 */
public final class BrtsValidatorFactory {

	private BrtsValidatorFactory() {
		// utility class
	}

	/** Returns the shared, thread-safe {@link Validator}. */
	public static Validator get() {
		return Holder.VALIDATOR;
	}

	private static final class Holder {

		private static final ValidatorFactory FACTORY = createFactory();

		private static final Validator VALIDATOR = FACTORY.getValidator();

		private static ValidatorFactory createFactory() {
			// Explicit provider instead of service discovery: the CLI fat jar does not merge META-INF/services.
			return Validation.byProvider(HibernateValidator.class).configure().defaultLocale(Locale.ENGLISH)
					.buildValidatorFactory();
		}

	}

}
