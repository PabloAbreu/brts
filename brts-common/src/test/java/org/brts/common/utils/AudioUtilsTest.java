/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/utils/AudioUtilsTest.java' is part of BRTS.
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

package org.brts.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioUtilsTest {

	@Test
	void mapsBluRaySampleRatesToCodes() {
		assertThat(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(AudioUtils.SAMPLE_RATE_48000_HZ))
				.isEqualTo(AudioUtils.BLURAY_AUDIO_SAMPLE_RATE_CODE_48000_HZ);
		assertThat(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(AudioUtils.SAMPLE_RATE_96000_HZ))
				.isEqualTo(AudioUtils.BLURAY_AUDIO_SAMPLE_RATE_CODE_96000_HZ);
		assertThat(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(AudioUtils.SAMPLE_RATE_192000_HZ))
				.isEqualTo(AudioUtils.BLURAY_AUDIO_SAMPLE_RATE_CODE_192000_HZ);
	}

	@Test
	void defaultsUnknownBluRaySampleRatesTo48KhzCode() {
		assertThat(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(null))
				.isEqualTo(AudioUtils.BLURAY_AUDIO_SAMPLE_RATE_CODE_48000_HZ);
		assertThat(AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(44_100))
				.isEqualTo(AudioUtils.BLURAY_AUDIO_SAMPLE_RATE_CODE_48000_HZ);
	}
}
