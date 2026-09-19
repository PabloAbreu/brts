package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/bdmv/GprStateTest.java' is part of BRTS.
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

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GprState}.
 */
class GprStateTest {

	@Test
	void unsetRegister_defaultsToZeroAndNotExplicitlySet() {
		GprState state = new GprState();
		assertThat(state.get(5)).isZero();
		assertThat(state.isExplicitlySet(5)).isFalse();
		assertThat(state.asMap()).doesNotContainKey(5);
	}

	@Test
	void explicitZero_isTrackedAsSetAndAppearsInMap() {
		GprState state = new GprState();
		state.set(5, 0L);
		assertThat(state.get(5)).isZero();
		assertThat(state.isExplicitlySet(5)).isTrue();
		assertThat(state.asMap()).containsEntry(5, 0L);
	}

	@Test
	void set_updatesValueAndFlag() {
		GprState state = new GprState();
		state.set(10, 42L);
		assertThat(state.get(10)).isEqualTo(42L);
		assertThat(state.isExplicitlySet(10)).isTrue();
	}

	@Test
	void constructorFromMap_marksAllEntriesExplicit() {
		GprState state = new GprState(Map.of(1, 100L, 2, 0L));
		assertThat(state.asMap()).containsEntry(1, 100L).containsEntry(2, 0L);
		assertThat(state.isExplicitlySet(1)).isTrue();
		assertThat(state.isExplicitlySet(2)).isTrue();
		assertThat(state.isExplicitlySet(3)).isFalse();
	}

	@Test
	void constructorFromNullMap_isSameAsEmpty() {
		GprState state = new GprState(null);
		assertThat(state.asMap()).isEmpty();
	}

	@Test
	void asMap_isUnmodifiableSnapshot() {
		GprState state = new GprState();
		state.set(1, 5L);
		Map<Integer, Long> snapshot = state.asMap();
		state.set(2, 6L);
		assertThat(snapshot).containsOnlyKeys(1);
	}

}
