package org.brts.lowlevel.clpi;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/clpi/M2tsClpiRegenBuilderTimestampUnwrapperTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class M2tsClpiRegenBuilderTimestampUnwrapperTest {

	@Test
	void unwrap_noWrap_valuesRemainUnchanged() {
		M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper u = new M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper(
				M2tsClpiRegenBuilder.PTS_MODULO);

		assertThat(u.unwrap(1234)).isEqualTo(1234);
		assertThat(u.unwrap(5678)).isEqualTo(5678);
		assertThat(u.unwrap(9876)).isEqualTo(9876);
	}

	@Test
	void unwrap_ptsWrap_addsOneModuloAfterBoundary() {
		M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper u = new M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper(
				M2tsClpiRegenBuilder.PTS_MODULO);

		long nearEnd = M2tsClpiRegenBuilder.PTS_MODULO - 120;
		assertThat(u.unwrap(nearEnd)).isEqualTo(nearEnd);
		assertThat(u.unwrap(42)).isEqualTo(M2tsClpiRegenBuilder.PTS_MODULO + 42);
		assertThat(u.unwrap(99)).isEqualTo(M2tsClpiRegenBuilder.PTS_MODULO + 99);
	}

	@Test
	void unwrap_smallBackwardJump_isNotTreatedAsWrap() {
		M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper u = new M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper(
				M2tsClpiRegenBuilder.PTS_MODULO);

		assertThat(u.unwrap(10_000)).isEqualTo(10_000);
		assertThat(u.unwrap(9_990)).isEqualTo(9_990);
	}

	@Test
	void unwrap_pcrWrap_worksIn27MHzDomain() {
		M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper u = new M2tsClpiRegenBuilder.MonotonicTimestampUnwrapper(
				M2tsClpiRegenBuilder.PCR_27MHZ_MODULO);

		long nearEnd = M2tsClpiRegenBuilder.PCR_27MHZ_MODULO - 90;
		assertThat(u.unwrap(nearEnd)).isEqualTo(nearEnd);
		assertThat(u.unwrap(45)).isEqualTo(M2tsClpiRegenBuilder.PCR_27MHZ_MODULO + 45);
	}

}
