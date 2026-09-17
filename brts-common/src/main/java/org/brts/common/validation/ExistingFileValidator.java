package org.brts.common.validation;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExistingFileValidator implements ConstraintValidator<ExistingFile, Object> {

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
			return fail(context, "file does not exist");
		}
		if (!Files.isRegularFile(path)) {
			return fail(context, "is not a regular file");
		}
		if (!Files.isReadable(path)) {
			return fail(context, "file is not readable");
		}
		return true;
	}

	private boolean fail(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
		return false;
	}

}
