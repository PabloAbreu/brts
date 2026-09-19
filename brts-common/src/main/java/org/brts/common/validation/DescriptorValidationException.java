package org.brts.common.validation;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * 
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/validation/DescriptorValidationException.java' is part of BRTS.
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

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.brts.common.exception.BrtsException;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;

/**
 * Thrown when a descriptor or CLI options bean fails Bean Validation, before any processing starts.
 */
@Getter
public class DescriptorValidationException extends BrtsException {

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
