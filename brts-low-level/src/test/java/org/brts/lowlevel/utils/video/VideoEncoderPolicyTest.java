/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/VideoEncoderPolicyTest.java' is part of BRTS.
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

import static org.brts.lowlevel.utils.video.VideoTestSupport.preview;
import static org.brts.lowlevel.utils.video.VideoTestSupport.request;
import static org.brts.lowlevel.utils.video.VideoTestSupport.sdr;
import static org.brts.lowlevel.utils.video.VideoTestSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VideoEncoderPolicyTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(Engine.class)
	void rateControlIsRequiredMutuallyExclusiveAndBoundedForEveryEngine(Engine engine) {
		var params = new VideoConversionParams();
		assertThrows(IllegalArgumentException.class, () -> VideoEncoderAdapter.rateControl(params, engine, Map.of()));
		for (int invalid : new int[] { -1, 0, 40001, Integer.MAX_VALUE }) {
			params.setBitrateKbps(invalid);
			assertThrows(IllegalArgumentException.class,
					() -> VideoEncoderAdapter.rateControl(params, engine, Map.of()));
		}
		params.setBitrateKbps(1);
		assertEquals(RateControl.VBR, VideoEncoderAdapter.rateControl(params, engine, Map.of()));
		params.setBitrateKbps(40000);
		params.setRateControl(RateControl.CBR);
		assertEquals(RateControl.CBR, VideoEncoderAdapter.rateControl(params, engine, Map.of()));
		params.setRateControl(RateControl.CRF);
		assertThrows(IllegalArgumentException.class, () -> VideoEncoderAdapter.rateControl(params, engine, Map.of()));
		params.setRateControl(null);
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.rateControl(params, engine, Map.of("cqp", "20")));
		params.setBitrateKbps(null);
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.rateControl(params, engine, Map.of("cqp", "20", "qvbr", "20")));
	}

	@ParameterizedTest
	@CsvSource({ "FFMPEG,crf,0,CRF", "FFMPEG,crf,23.5,CRF", "FFMPEG,crf,51,CRF", "NVENCC,cqp,0,CQP",
			"NVENCC,cqp,51,CQP", "NVENCC,cqp,20:22:24,CQP", "NVENCC,qvbr,1,QVBR", "NVENCC,qvbr,23.5,QVBR",
			"VCEENCC,cqp,20,CQP", "VCEENCC,qvbr,51,QVBR", "QSVENCC,icq,1,ICQ", "QSVENCC,icq,51,ICQ",
			"QSVENCC,la-icq,20,LA_ICQ", "QSVENCC,cqp,20:22:24,CQP" })
	void nativeQualityModesAreNotReinterpreted(Engine engine, String key, String quality, RateControl mode)
			throws IOException {
		var params = request(directory);
		params.setBitrateKbps(null);
		params.setEngine(engine);
		params.setEngineOptions(Map.of(key, quality));
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals(mode, plan.rateControl());
		assertEquals(quality, value(plan.command(), (engine == Engine.FFMPEG ? "-" : "--") + key));
		if (mode == RateControl.CQP || mode == RateControl.ICQ || mode == RateControl.LA_ICQ) {
			assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("cannot enforce")));
			assertFalse(plan.command().contains("--max-bitrate"));
			assertFalse(plan.command().contains("--bluray"));
		}
		params.setRateControl(mode == RateControl.CRF ? RateControl.CQP : RateControl.CRF);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
	}

	@ParameterizedTest
	@CsvSource({ "FFMPEG,cqp", "FFMPEG,icq", "NVENCC,crf", "NVENCC,icq", "VCEENCC,crf", "VCEENCC,la-icq", "QSVENCC,crf",
			"QSVENCC,qvbr" })
	void nonnativeQualityModesFailRatherThanFallBack(Engine engine, String key) {
		var params = new VideoConversionParams();
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.rateControl(params, engine, Map.of(key, "20")));
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(engine, Map.of(key, "20")));
	}

	@ParameterizedTest
	@CsvSource({ "crf,-1", "crf,51.01", "crf,NaN", "crf,Infinity", "crf,-Infinity", "crf,1:2:3", "cqp,-1", "cqp,52",
			"cqp,20.5", "cqp,20:21", "cqp,20:21:52", "cqp,20::22", "cqp,NaN", "icq,0", "icq,52", "icq,20.5",
			"icq,Infinity", "la-icq,0", "la-icq,NaN", "qvbr,0", "qvbr,52", "qvbr,NaN", "qvbr,Infinity", "qvbr,1:2:3",
			"crf,not-a-number" })
	void qualityMustBeFiniteWithinNativeRangeAndCorrectShape(String key, String quality) {
		Engine engine = key.equals("crf") ? Engine.FFMPEG : key.contains("icq") ? Engine.QSVENCC : Engine.NVENCC;
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.rateControl(new VideoConversionParams(), engine, Map.of(key, quality)));
	}

	@ParameterizedTest
	@EnumSource(Engine.class)
	void protectedOptionKeysCannotOverrideComplianceOrShellArguments(Engine engine) {
		for (String key : new String[] { "i", "o", "input", "output", "codec", "r", "fps", "g", "gop-len", "bframes",
				"ref", "slices", "sar", "profile", "level", "tier", "output-depth", "output-csp", "pix_fmt", "vf",
				"vpp-pad", "vpp-colorspace", "x264-params", "x265-params", "maxrate", "max-bitrate", "vbv-bufsize",
				"colorprim", "master-display", "extra-args", "--preset", "preset;touch", "allow-non-conformance" }) {
			assertThrows(IllegalArgumentException.class,
					() -> VideoEncoderAdapter.validateOptions(engine, Map.of(key, "1")), key);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "slow\n-i evil", "slow\r", "slow\u0000", "slow; touch file", "placebo" })
	void unsafeOrUnknownOptionValuesAreRejected(String value) {
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(Engine.FFMPEG, Map.of("preset", value)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "0", "1025", "2.5", "999999999999999999999999", "1;echo" })
	void threadCountMustBeSmallPositiveInteger(String value) {
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(Engine.FFMPEG, Map.of("threads", value)));
	}

	@Test
	void deviceAndPresetEnumsAreValidatedPerEngine() {
		VideoEncoderAdapter.validateOptions(Engine.FFMPEG, Map.of("threads", "1024", "preset", "slow"));
		VideoEncoderAdapter.validateOptions(Engine.NVENCC, Map.of("device", "0", "preset", "P7"));
		VideoEncoderAdapter.validateOptions(Engine.VCEENCC, Map.of("device", "1", "preset", "balanced"));
		VideoEncoderAdapter.validateOptions(Engine.QSVENCC, Map.of("quality", "best"));
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(Engine.NVENCC, Map.of("device", "1025")));
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(Engine.QSVENCC, Map.of("preset", "slow")));
		assertThrows(IllegalArgumentException.class,
				() -> VideoEncoderAdapter.validateOptions(Engine.VCEENCC, Map.of("preset", "P7")));
	}

	@Test
	void vceQvbrHevcAndFractionalQualityAreRejected() throws IOException {
		var params = request(directory);
		params.setBitrateKbps(null);
		params.setEngine(Engine.VCEENCC);
		params.setCodec(StreamCodingType.H265_HEVC);
		params.setEngineOptions(Map.of("qvbr", "20"));
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
		params.setCodec(StreamCodingType.H264_AVC);
		params.setEngineOptions(Map.of("qvbr", "20.5"));
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
	}

	@Test
	void cbrCommandUsesRequestedRateAndProtectedVbvCap() throws IOException {
		var params = request(directory);
		params.setRateControl(RateControl.CBR);
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals("20000k", value(plan.command(), "-minrate"));
		assertEquals("20000k", plan.command().get(plan.command().lastIndexOf("-maxrate") + 1));
		assertTrue(value(plan.command(), "-x264-params").contains("vbv-maxrate=20000:vbv-bufsize=30000:nal-hrd=cbr"));
		params.setEngine(Engine.NVENCC);
		plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals("20000", value(plan.command(), "--cbr"));
		assertEquals("20000", value(plan.command(), "--max-bitrate"));
	}

	@ParameterizedTest
	@CsvSource({ "vbr,20000", "cbr,20000", "icq,20", "la-icq,20", "cqp,20:22:24" })
	void qsvDisablesAutomaticRateControlFallbackForEverySupportedMode(String mode, String quality) throws IOException {
		var params = request(directory);
		params.setEngine(Engine.QSVENCC);
		if (mode.equals("cbr")) {
			params.setRateControl(RateControl.CBR);
		} else if (!mode.equals("vbr")) {
			params.setBitrateKbps(null);
			params.setEngineOptions(Map.of(mode, quality));
		}
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals(quality, value(plan.command(), "--" + mode));
		assertEquals(1, plan.command().stream().filter("--no-fallback-rc"::equals).count());
	}
}
