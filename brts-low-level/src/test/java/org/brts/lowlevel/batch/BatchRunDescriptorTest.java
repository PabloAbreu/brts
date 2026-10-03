/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/batch/BatchRunDescriptorTest.java' is part of BRTS.
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

package org.brts.lowlevel.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.junit.jupiter.api.Test;

class BatchRunDescriptorTest {

	@Test
	void parsesAndBuildsTokensSkippingNullValues() throws Exception {
		String json = """
				{ "steps": [
				  { "comment": "build" },
				  { "level": "low", "command": "index-write",
				    "arguments": [ { "flag": "--input", "value": "i.json" }, { "flag": "--error-details" } ] }
				] }""";
		BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(json, BatchRunDescriptor.class);

		DescriptorValidator.validateOrThrow(descriptor, "batch");
		assertThat(descriptor.getSteps().get(1).toCommandTokens()).containsExactly("index-write", "--input", "i.json",
				"--error-details");
	}

	@Test
	void rejectsEmptyAndMalformedSteps() {
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(new BatchRunDescriptor(), "batch"))
				.isInstanceOf(DescriptorValidationException.class).hasMessageContaining("steps");

		BatchStep missingLevel = new BatchStep();
		missingLevel.setCommand("index-write");
		BatchStep empty = new BatchStep();
		BatchStep argsWithoutCommand = BatchStep.comment("x");
		argsWithoutCommand.setArguments(List.of(new BatchArgument("--a", "b")));
		BatchStep blankFlag = BatchStep.invocation("low", "index-write", List.of(new BatchArgument(" ", "v")));

		for (BatchStep step : List.of(missingLevel, empty, argsWithoutCommand, blankFlag)) {
			BatchRunDescriptor descriptor = new BatchRunDescriptor();
			descriptor.getSteps().add(step);
			assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(descriptor, "batch"))
					.isInstanceOf(DescriptorValidationException.class);
		}
	}

}
