/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/M2tsStreamInfoTest.java' is part of BRTS.
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

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.video.FrameRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class M2tsStreamInfoTest {
	@ParameterizedTest
	@CsvSource({ "1,23.976", "2,24.0", "3,25.0", "4,29.97", "6,50.0", "7,59.94" })
	void rateCodesProvideCustomaryFps(int code, double fps) {
		assertThat(IStreamInfo.fpsFromFrameRate(code)).isEqualTo(fps);
		assertThat(IStreamInfo.frameRateFromFps(fps)).isEqualTo(code);
		assertThat(IStreamInfo.frameRateFromFps(FrameRate.fromCode(code).asDouble())).isEqualTo(code);
		var stream = new M2tsStreamInfo();
		stream.setFrameRate(code);
		assertThat(stream.getFrameRateFps()).isEqualTo(fps);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 5, 8, 15, 99 })
	void unsupportedRateCodesHaveNoFps(int code) {
		assertThat(IStreamInfo.fpsFromFrameRate(code)).isNull();
	}

	@Test
	void nullFpsDefaultsTo23976() {
		assertThat(IStreamInfo.frameRateFromFps(null)).isEqualTo(FrameRate.FPS_23976.frameRateCode());
	}

	@ParameterizedTest
	@ValueSource(doubles = { -24.0, 0.0, 27.0, 30.0, 60.0, Double.NaN, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY })
	void unsupportedOrUncodedFpsAreRejected(double fps) {
		assertThatIllegalArgumentException().isThrownBy(() -> IStreamInfo.frameRateFromFps(fps))
				.withMessage("Unsupported frame rate: " + fps);
	}

	@Test
	void parserRateCodeAndVfrProvideFpsWithoutExplicitOverride() {
		var stream = new M2tsStreamInfo();
		assertThat(stream.getFrameRateFps()).isNull();
		stream.setFrameRate(1);
		assertThat(stream.getFrameRateFps()).isEqualTo(23.976);
		stream.setFrameRate(2);
		assertThat(stream.getFrameRateFps()).isEqualTo(24.0);
		stream.setVfr(0x64);
		assertThat(stream.getFrameRateFps()).isEqualTo(29.97);
		assertThat(stream.getVideoFormat()).isEqualTo(6);
		stream.setFrameRate(0);
		assertThat(stream.getFrameRateFps()).isNull();
	}

	@Test
	void explicitOverrideWinsAndCanBeCleared() {
		var stream = new M2tsStreamInfo();
		stream.setFrameRate(1);
		stream.setFrameRateFps(25.0);
		assertThat(stream.getFrameRateFps()).isEqualTo(25.0);
		stream.setFrameRateFps(null);
		assertThat(stream.getFrameRateFps()).isEqualTo(23.976);
	}
}
