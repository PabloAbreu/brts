package org.brts.common.validation;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.brts.common.exception.BrtException;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;

/**
 * Thrown when a descriptor or CLI options bean fails Bean Validation, before any processing starts.
 */
@Getter
public class DescriptorValidationException extends BrtException {

	private static final long serialVersionUID = 1L;

	private final transient Set<ConstraintViolation<Object>> violations;

	public DescriptorValidationException(String subject, Set<ConstraintViolation<Object>> violations) {
		super(buildMessage(subject, violations));
		this.violations = new LinkedHashSet<>(violations);
	}

	private static String buildMessage(String subject, Set<ConstraintViolation<Object>> violations) {
		String details = violations.stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
				.map(DescriptorValidationException::format).collect(Collectors.joining(System.lineSeparator()));
		return "Invalid " + subject + " (" + violations.size() + " problem(s)):" + System.lineSeparator() + details;
	}

	private static String format(ConstraintViolation<Object> violation) {
		String path = violation.getPropertyPath().toString();
		String location = path.isEmpty() ? "<root>" : path;
		Object value = violation.getInvalidValue();
		String actual = value == null ? "" : " (actual: " + value + ")";
		return "  - " + location + ": " + violation.getMessage() + actual;
	}

}
