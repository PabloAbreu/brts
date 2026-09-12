package org.brts.lowlevel.bdmv;

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
