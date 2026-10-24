/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsSampleExtrapolatorTest.java' is part of BRTS.
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

import java.util.List;

import org.junit.jupiter.api.Test;

class M2tsSampleExtrapolatorTest {

	@Test
	void extrapolatesSeveralSamplesAndLabelsEstimateNonGuaranteed() {
		var estimate = new M2tsSampleExtrapolator().extrapolate(List.of(new M2tsSampleEncoder.SampleResult(90_000, 100),
				new M2tsSampleEncoder.SampleResult(90_000, 200)), 180_000);

		assertThat(estimate.projectedVideoBytes()).isEqualTo(300);
		assertThat(estimate.sampleCount()).isEqualTo(2);
		assertThat(estimate.guaranteedUpperBound()).isFalse();
	}

	@Test
	void refusesInvalidSampleCoverage() {
		var extrapolator = new M2tsSampleExtrapolator();
		assertThatThrownBy(() -> extrapolator.extrapolate(List.of(), 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> extrapolator.extrapolate(List.of(new M2tsSampleEncoder.SampleResult(101, 1)), 100))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceed");
	}
}
