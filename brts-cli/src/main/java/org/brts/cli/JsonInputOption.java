package org.brts.cli;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER })
/**
 * Extends the possibilities of {@link Option} to parse a POJO from a JSON input file.
 *
 * Instead of writing
 *
 * <pre>
 *      @Option(name = "--descriptor", required = true, usage = "Path to the clip JSON descriptor")
 *      File descriptor;
 * </pre>
 *
 * and then parsing the JSON file manually in the {@code FeatureRunner.execute()} method,
 * you can write
 *
 * <pre>
 *      @JsonInputOption(name = "--descriptor", required = true, usage = "Path to the clip JSON descriptor")
 *      ClipInfo clipInfo;
 * </pre>
 */
public @interface JsonInputOption {

	// all @org.kohsuke.args4j.Option fields
	String name();

	String usage() default "";

	String[] aliases() default {};

	String metaVar() default "";

	boolean required() default false;

	boolean help() default false;

	boolean hidden() default false;

	// handler is hard-coded
	String[] depends() default {};

	String[] forbids() default {};

}
