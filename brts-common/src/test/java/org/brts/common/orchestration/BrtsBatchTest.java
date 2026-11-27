/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/orchestration/BrtsBatchTest.java' is part of BRTS.
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

package org.brts.common.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BrtsBatchTest {

	@Test
	void buildsOrderedProgrammaticBatch() {
		BrtsBatch batch = BrtsBatch.builder().add(BrtsInvocation.builder("low", "one").argument("--input", "a").build())
				.add(BrtsInvocation.builder("mid", "two").flag("--force").build())
				.add(BrtsInvocation.builder("high", "three").argument("--output", "b").build()).build();

		assertThat(batch.steps()).hasSize(3);
		assertThat(batch.steps()).extracting(step -> step.invocation().command()).containsExactly("one", "two",
				"three");
		assertThat(batch.steps().get(1).invocation().toTokens()).containsExactly("mid", "two", "--force");
	}

	@Test
	void preservesEmptyPreflightAndImmutableSteps() {
		BrtsBatch batch = BrtsBatch.builder().add(BrtsInvocation.builder("low", "run").build()).build();

		assertThat(batch.preflightSteps()).isEqualTo(List.of());
		assertThat(batch.steps()).hasSize(1);
	}
}
