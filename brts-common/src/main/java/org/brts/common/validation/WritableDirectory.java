package org.brts.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated {@link String}, {@link java.io.File} or {@link java.nio.file.Path} must denote a writable output
 * directory. {@code null} and blank values are considered valid; combine with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = WritableDirectoryValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface WritableDirectory {

	String message() default "must be a writable directory";

	/** When true, a missing directory is accepted as long as the closest existing ancestor is writable. */
	boolean createIfMissing() default false;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
