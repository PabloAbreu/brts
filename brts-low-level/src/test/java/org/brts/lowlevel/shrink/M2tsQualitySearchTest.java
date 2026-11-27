/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsQualitySearchTest.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class M2tsQualitySearchTest {

	private final M2tsQualitySearch search = new M2tsQualitySearch();

	@Test
	void findsBestFeasibleBoundaryWhenHigherValueShrinksOutput() {
		var result = search.search(1, 100, 500, 20, true, value -> 1_000 - value * 10L);

		assertThat(result.qualityValue()).isEqualTo(50);
		assertThat(result.estimatedBytes()).isEqualTo(500);
		assertThat(result.converged()).isTrue();
		assertThat(result.iterations()).isLessThanOrEqualTo(7);
	}

	@Test
	void handlesOppositeMonotonicDirectionAndIterationLimit() {
		var result = search.search(1, 100, 500, 1, false, value -> value * 10L);

		assertThat(result.qualityValue()).isEqualTo(50);
		assertThat(result.converged()).isFalse();
		assertThat(result.iterations()).isEqualTo(1);
	}

	@Test
	void rejectsUnbracketedOrNegativeEstimates() {
		assertThatThrownBy(() -> search.search(1, 10, 100, 5, true, value -> 1_000L))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No feasible");
		assertThatThrownBy(() -> search.search(1, 10, 100, 5, true, value -> -1L))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be negative");
		assertThatThrownBy(
				() -> search.search(1, 100, 500, 20, true, value -> value == 50 ? 2_000L : 1_000L - value * 10L))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("monotonic direction");
	}
}
