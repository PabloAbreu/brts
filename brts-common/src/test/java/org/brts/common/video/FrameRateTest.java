/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/video/FrameRateTest.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FrameRateTest {
	@ParameterizedTest
	@CsvSource({ "1,24000,1001", "2,24,1", "3,25,1", "4,30000,1001", "6,50,1", "7,60000,1001" })
	void looksUpCodedRates(int code, int numerator, int denominator) {
		FrameRate rate = FrameRate.fromCode(code);
		assertThat(rate).isEqualTo(new FrameRate(numerator, denominator));
		assertThat(rate.frameRateCode()).isEqualTo(code);
		assertThat(rate.asDouble()).isEqualTo((double) numerator / denominator);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 5, 8, 15, 99 })
	void rejectsUnsupportedCodes(int code) {
		assertThatIllegalArgumentException().isThrownBy(() -> FrameRate.fromCode(code))
				.withMessage("Unsupported frame rate code: " + code);
	}

	@Test
	void equivalentFractionsRetainCanonicalCodes() {
		assertThat(FrameRate.of(48000, 2002)).isSameAs(FrameRate.FPS_23976);
		assertThat(FrameRate.parse("48/2")).isSameAs(FrameRate.FPS_24);
		assertThat(new FrameRate(48000, 2002)).isEqualTo(FrameRate.FPS_23976);
		assertThat(FrameRate.parse("23.976")).isSameAs(FrameRate.FPS_23976);
	}

	@Test
	void uncodedRatesKeepTheirTimingWithoutInventingACode() {
		assertThat(FrameRate.of(27, 1)).isEqualTo(new FrameRate(27, 1, -1));
		assertThat(FrameRate.FPS_30.frameRateCode()).isEqualTo(-1);
		assertThat(FrameRate.FPS_60.frameRateCode()).isEqualTo(-1);
		assertThatIllegalArgumentException().isThrownBy(() -> FrameRate.of(1, 0));
	}
}
