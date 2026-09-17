package org.brts.common.validation;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.ConstraintViolation;

/**
 * Entry point for annotation-driven validation of descriptors and CLI options beans.
 * <p>
 * Constraints are declared with standard {@code jakarta.validation} annotations on the bean fields; nested descriptors
 * are validated when the holding field is annotated with {@code @Valid}.
 */
public final class DescriptorValidator {

	private DescriptorValidator() {
		// utility class
	}

	/** Validates the bean graph and returns the violations, empty when the bean is valid. */
	public static Set<ConstraintViolation<Object>> validate(Object bean) {
		if (bean == null) {
			return Set.of();
		}
		return deduplicate(BrtsValidatorFactory.get().validate(bean));
	}

	/**
	 * Lombok mirrors field annotations onto generated getters, so the same constraint can be reported twice for one
	 * property. Collapse identical (path, message) pairs so the user sees each problem once.
	 */
	private static Set<ConstraintViolation<Object>> deduplicate(Set<ConstraintViolation<Object>> violations) {
		Set<String> seen = new HashSet<>();
		Set<ConstraintViolation<Object>> unique = new LinkedHashSet<>();
		for (ConstraintViolation<Object> violation : violations) {
			if (seen.add(violation.getPropertyPath() + "|" + violation.getMessage())) {
				unique.add(violation);
			}
		}
		return unique;
	}

	/**
	 * Validates the bean graph and throws when any constraint is violated.
	 *
	 * @param bean    the bean to validate
	 * @param subject human-readable name of what is being validated, used in the error message
	 */
	public static void validateOrThrow(Object bean, String subject) {
		Set<ConstraintViolation<Object>> violations = validate(bean);
		if (!violations.isEmpty()) {
			throw new DescriptorValidationException(subject, violations);
		}
	}

}
