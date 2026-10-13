/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/FrameRateAndConstraintsTest.java' is part of BRTS.
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
package org.brts.lowlevel.utils.video;

import static org.brts.common.model.StreamCodingType.H264_AVC;
import static org.brts.common.model.StreamCodingType.H265_HEVC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FrameRateAndConstraintsTest {

	@ParameterizedTest
	@CsvSource({ "23.976,24000,1001,24", "24000/1001,24000,1001,24", "24,24,1,24", "25,25,1,25", "29.97,30000,1001,30",
			"30,30,1,30", "50,50,1,50", "59.94,60000,1001,60", "60,60,1,60", "48000/2002,24000,1001,24",
			"48/2,24,1,24" })
	void parsesCanonicalRatesAndNominalOneSecondGop(String text, int numerator, int denominator, int gop) {
		FrameRate rate = FrameRate.parse(text);
		assertEquals(new FrameRate(numerator, denominator), rate);
		assertEquals(numerator + "/" + denominator, rate.toString());
		assertEquals(gop, rate.nominalGopFrames());
		assertEquals((double) numerator / denominator, rate.asDouble());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "0", "-24", "27", "NaN", "Infinity", "24/0", "0/1", "-24/1", "24/-1", "24/1/2",
			"not-a-rate", "24/", "/1" })
	void rejectsInvalidRates(String value) {
		assertThrows(IllegalArgumentException.class, () -> FrameRate.parse(value));
	}

	@Test
	void exactRationalDoesNotRoundUnknownTimingIntoASupportedRate() {
		FrameRate unknown = FrameRate.of(27, 1);
		assertFalse(BluRayVideoConstraints.supports(H265_HEVC, 1920, 1080, unknown));
		assertThrows(IllegalArgumentException.class, () -> FrameRate.of(1, 0));
		assertThrows(IllegalArgumentException.class, () -> FrameRate.of(-1, 1));
	}

	@Test
	void progressiveAvcTuplesAreNotJustDimensionChecks() {
		assertTrue(BluRayVideoConstraints.supports(H264_AVC, 1920, 1080, FrameRate.FPS_24));
		assertTrue(BluRayVideoConstraints.supports(H264_AVC, 1920, 1080, FrameRate.FPS_23976));
		assertFalse(BluRayVideoConstraints.supports(H264_AVC, 1920, 1080, FrameRate.FPS_25));
		assertFalse(BluRayVideoConstraints.supports(H264_AVC, 1920, 1080, FrameRate.FPS_5994));
		assertTrue(BluRayVideoConstraints.supports(H264_AVC, 1280, 720, FrameRate.FPS_50));
		assertTrue(BluRayVideoConstraints.supports(H264_AVC, 1280, 720, FrameRate.FPS_5994));
		assertFalse(BluRayVideoConstraints.supports(H264_AVC, 1280, 720, FrameRate.FPS_60));
		assertFalse(BluRayVideoConstraints.supports(H264_AVC, 720, 576, FrameRate.FPS_25));
		assertThrows(IllegalArgumentException.class, () -> BluRayVideoConstraints.formats(null));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1920, 3840 })
	void everyHevcRateIsAllowedOnlyForSupportedCanvases(int width) {
		int height = width == 1920 ? 1080 : 2160;
		for (FrameRate rate : FrameRate.supportedRates()) {
			assertTrue(BluRayVideoConstraints.supports(H265_HEVC, width, height, rate), rate.toString());
			assertFalse(BluRayVideoConstraints.supports(H265_HEVC, width, height - 2, rate));
		}
		assertFalse(BluRayVideoConstraints.supports(H265_HEVC, 1280, 720, FrameRate.FPS_24));
	}

	@Test
	void formatTablesAndRateListsAreImmutableSnapshots() {
		List<FrameRate> mutable = new ArrayList<>(List.of(FrameRate.FPS_24));
		var format = new BluRayVideoConstraints.Format(1920, 1080, mutable);
		mutable.clear();
		assertEquals(List.of(FrameRate.FPS_24), format.frameRates());
		assertThrows(UnsupportedOperationException.class, () -> format.frameRates().clear());
		assertThrows(UnsupportedOperationException.class, () -> FrameRate.supportedRates().clear());
		assertThrows(UnsupportedOperationException.class, () -> BluRayVideoConstraints.formats(H264_AVC).clear());
	}

	@Test
	void invalidProbeDimensionsAndSarAreRejectedWithoutNativeCode() {
		assertThrows(IllegalArgumentException.class, () -> VideoTestSupport.sdr(0, 1080, FrameRate.FPS_24));
		assertThrows(IllegalArgumentException.class, () -> new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 0, 1, true,
				"h264", null, null, null, false, null, null));
	}

	@Test
	void legacyProbeConstructorDoesNotInventEncodingOrRangeMetadata() {
		var source = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "h264", "bt709", "bt709", "bt709",
				false, null, null);
		assertEquals(-1, source.bitDepth());
		assertEquals(-1, source.chromaWidthShift());
		assertEquals(-1, source.chromaHeightShift());
		assertEquals(-1, source.profile());
		assertEquals(-1, source.level());
		assertEquals(-1, source.chromaLocation());
		assertFalse(source.rangeKnown());
		assertFalse(source.fullRange(), "A false legacy range flag does not establish known limited range");
	}
}
