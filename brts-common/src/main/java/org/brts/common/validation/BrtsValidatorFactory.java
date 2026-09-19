package org.brts.common.validation;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/validation/BrtsValidatorFactory.java' is part of BRTS.
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
