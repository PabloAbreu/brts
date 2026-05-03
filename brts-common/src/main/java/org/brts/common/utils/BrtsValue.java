package org.brts.common.utils;

import java.lang.annotation.ElementType;
// retention run-time for field annotations
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to mark fields in descriptor classes that can have default values from BrtsFileConfig properties.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE })
public @interface BrtsValue {
	/**
	 * The name of the property suffix to read from BrtsFileConfig. If not specified, the field name is used.
	 *
	 * @return
	 */
	String value() default "";
}
