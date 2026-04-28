package org.brts.lowlevel.clpi;

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
