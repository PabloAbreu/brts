/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-doc-generator/src/test/java/org/brts/doc/generator/GeneratorArgumentsTest.java' is part of BRTS.
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

package org.brts.doc.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GeneratorArgumentsTest {

	private static final String REQUIRED_ARGUMENTS = "Required arguments: --input <metadata.json> --output <completion-file>";

	@Test
	void parsesOptionsInEitherOrderAndUsesLastRepeatedValue() {
		GeneratorArguments inputThenOutput = GeneratorArguments.parse(
				new String[] { "--input", "first.json", "--output", "first.out", "--input", "metadata.json" },
				REQUIRED_ARGUMENTS);
		GeneratorArguments outputThenInput = GeneratorArguments.parse(
				new String[] { "--output", "first.out", "--input", "metadata.json", "--output", "result.out" },
				REQUIRED_ARGUMENTS);

		assertThat(inputThenOutput).isEqualTo(new GeneratorArguments(Path.of("metadata.json"), Path.of("first.out")));
		assertThat(outputThenInput).isEqualTo(new GeneratorArguments(Path.of("metadata.json"), Path.of("result.out")));
	}

	@Test
	void rejectsUnknownArguments() {
		assertThatThrownBy(() -> GeneratorArguments.parse(new String[] { "--unknown" }, REQUIRED_ARGUMENTS))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown argument: --unknown");
	}

	@Test
	void rejectsMissingOptionValues() {
		assertThatThrownBy(() -> GeneratorArguments.parse(new String[] { "--input" }, REQUIRED_ARGUMENTS))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Missing value for --input");
		assertThatThrownBy(() -> GeneratorArguments.parse(new String[] { "--output" }, REQUIRED_ARGUMENTS))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Missing value for --output");
	}

	@Test
	void requiresBothOptionsAndUsesCallerSpecificMessage() {
		assertThatThrownBy(() -> GeneratorArguments.parse(new String[0], REQUIRED_ARGUMENTS))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(REQUIRED_ARGUMENTS);
		assertThatThrownBy(() -> GeneratorArguments.parse(new String[] { "--input", "metadata.json" },
				"Required arguments: --input <metadata.json> --output <directory>"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Required arguments: --input <metadata.json> --output <directory>");
	}
}
