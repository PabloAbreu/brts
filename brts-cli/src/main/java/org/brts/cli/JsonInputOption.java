package org.brts.cli;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/JsonInputOption.java' is part of BRTS.
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
 * @Option(name = "--descriptor", required = true, usage = "Path to the clip JSON descriptor")
 * File descriptor;
 * </pre>
 *
 * and then parsing the JSON file manually in the {@code FeatureRunner.execute()} method, you can write
 *
 * <pre>
 * @JsonInputOption(name = "--descriptor", required = true, usage = "Path to the clip JSON descriptor")
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
