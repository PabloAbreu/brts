package org.brts.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated {@link String}, {@link java.io.File} or {@link java.nio.file.Path} must point to an existing, readable
 * regular file. {@code null} and blank values are considered valid; combine with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = ExistingFileValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExistingFile {

	String message() default "must be an existing readable file";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
