package org.brts.common.validation;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/validation/ExistingDirectoryValidator.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExistingDirectoryValidator implements ConstraintValidator<ExistingDirectory, Object> {

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (PathValues.isMalformed(value)) {
			return fail(context, "is not a valid filesystem path");
		}
		Path path = PathValues.toPath(value);
		if (path == null) {
			return true;
		}
		if (!Files.exists(path)) {
			return fail(context, "directory does not exist");
		}
		if (!Files.isDirectory(path)) {
			return fail(context, "is not a directory");
		}
		return true;
	}

	private boolean fail(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
		return false;
	}

}
