package org.brts.common.validation;

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
