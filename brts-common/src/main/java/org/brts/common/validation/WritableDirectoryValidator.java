package org.brts.common.validation;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WritableDirectoryValidator implements ConstraintValidator<WritableDirectory, Object> {

	private boolean createIfMissing;

	@Override
	public void initialize(WritableDirectory constraintAnnotation) {
		this.createIfMissing = constraintAnnotation.createIfMissing();
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (PathValues.isMalformed(value)) {
			return fail(context, "is not a valid filesystem path");
		}
		Path path = PathValues.toPath(value);
		if (path == null) {
			return true;
		}
		if (Files.exists(path)) {
			if (!Files.isDirectory(path)) {
				return fail(context, "exists but is not a directory");
			}
			return Files.isWritable(path) ? true : fail(context, "directory is not writable");
		}
		if (!createIfMissing) {
			return fail(context, "directory does not exist");
		}
		Path ancestor = closestExistingAncestor(path);
		if (ancestor == null) {
			return fail(context, "directory cannot be created, no existing parent");
		}
		if (!Files.isDirectory(ancestor) || !Files.isWritable(ancestor)) {
			return fail(context, "directory cannot be created, closest existing parent is not writable");
		}
		return true;
	}

	private static Path closestExistingAncestor(Path path) {
		for (Path current = path.toAbsolutePath().getParent(); current != null; current = current.getParent()) {
			if (Files.exists(current)) {
				return current;
			}
		}
		return null;
	}

	private boolean fail(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
		return false;
	}

}
