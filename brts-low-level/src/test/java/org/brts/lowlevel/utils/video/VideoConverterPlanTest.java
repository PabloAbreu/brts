/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/VideoConverterPlanTest.java' is part of BRTS.
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

import org.brts.common.video.FrameRate;

import static org.brts.common.model.StreamCodingType.H265_HEVC;
import static org.brts.lowlevel.utils.video.VideoTestSupport.preview;
import static org.brts.lowlevel.utils.video.VideoTestSupport.request;
import static org.brts.lowlevel.utils.video.VideoTestSupport.sdr;
import static org.brts.lowlevel.utils.video.VideoTestSupport.value;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VideoConverterPlanTest {

	@TempDir
	Path directory;

	@Test
	void letterboxPreserves1920By900ContentAndAdds90RowsEachSide() throws IOException {
		var plan = preview(sdr(1920, 900, FrameRate.FPS_23976)).prepare(request(directory));
		assertAll(() -> assertEquals(1920, plan.width()), () -> assertEquals(1080, plan.height()),
				() -> assertEquals(1920, plan.contentWidth()), () -> assertEquals(900, plan.contentHeight()),
				() -> assertEquals(0, plan.padLeft()), () -> assertEquals(0, plan.padRight()),
				() -> assertEquals(90, plan.padTop()), () -> assertEquals(90, plan.padBottom()),
				() -> assertEquals(24, plan.gopFrames()), () -> assertEquals(3, plan.bFrames()),
				() -> assertEquals(40000, plan.maxBitrateKbps()), () -> assertEquals(30000, plan.vbvBufferKbits()));
		assertTrue(value(plan.command(), "-vf").startsWith("scale=1920:900:"));
		assertTrue(value(plan.command(), "-vf").contains("pad=1920:1080:0:90:color=black"));
		assertTrue(value(plan.command(), "-x264-params").contains("keyint=24:min-keyint=24:scenecut=0"));
		assertTrue(value(plan.command(), "-x264-params").contains("bluray-compat=1"));
		assertEquals("h264", plan.command().get(plan.command().lastIndexOf("-f") + 1));
		assertEquals(plan.outputPath().toString(), plan.command().getLast());
		assertTrue(plan.warnings().isEmpty());
	}

	@Test
	void horizontalPaddingIsCenteredAndChromaAligned() throws IOException {
		var plan = preview(sdr(1600, 1080, FrameRate.FPS_24)).prepare(request(directory));
		assertEquals(1600, plan.contentWidth());
		assertEquals(1080, plan.contentHeight());
		assertEquals(160, plan.padLeft());
		assertEquals(160, plan.padRight());
		assertEquals(0, plan.padTop() + plan.padBottom());
		var remainder = preview(sdr(1918, 1080, FrameRate.FPS_24)).prepare(request(directory));
		assertEquals(0, remainder.padLeft());
		assertEquals(2, remainder.padRight());
	}

	@Test
	void explicitTargetScalesWithoutCroppingAndAccountsForSampleAspectRatio() throws IOException {
		var params = request(directory);
		params.setWidth(1920);
		params.setHeight(1080);
		var plan = preview(sdr(640, 480, FrameRate.FPS_24)).prepare(params);
		assertEquals(1440, plan.contentWidth());
		assertEquals(1080, plan.contentHeight());
		assertEquals(240, plan.padLeft());
		assertEquals(240, plan.padRight());
		var anamorphic = new VideoStreamInfo(1440, 1080, FrameRate.FPS_24, 4, 3, true, "h264", "bt709", "bt709",
				"bt709", false, null, null);
		plan = preview(anamorphic).prepare(params);
		assertEquals(1920, plan.contentWidth());
		assertEquals(1080, plan.contentHeight());
		assertEquals(0, plan.padLeft() + plan.padRight() + plan.padTop() + plan.padBottom());
		assertTrue(value(plan.command(), "-vf").contains("setsar=1"));
		params.setWidth(null);
		params.setHeight(null);
		assertThrows(IllegalArgumentException.class, () -> preview(anamorphic).prepare(params));
	}

	@ParameterizedTest
	@CsvSource({ "1920,901", "1919,1080", "1000,600", "2000,1080", "1920,1200" })
	void rejectsOddAmbiguousOrOversizedImplicitGeometry(int width, int height) throws IOException {
		var params = request(directory);
		assertThrows(IllegalArgumentException.class,
				() -> preview(sdr(width, height, FrameRate.FPS_24)).prepare(params));
	}

	@Test
	void validatesPairedTargetResolutionAndFullAvcTuple() throws IOException {
		var params = request(directory);
		params.setWidth(1920);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
		params.setHeight(900);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
		params.setWidth(null);
		params.setHeight(null);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_25)).prepare(params));
		assertThrows(IllegalArgumentException.class,
				() -> preview(sdr(1920, 1080, FrameRate.of(27, 1))).prepare(params));
	}

	@ParameterizedTest
	@ValueSource(strings = { "23.976", "24", "25", "29.97", "30", "50", "59.94", "60" })
	void hevcCommandsUseExactFrameRatesAndMatchingGop(String text) throws IOException {
		var params = request(directory);
		params.setCodec(H265_HEVC);
		FrameRate rate = FrameRate.parse(text);
		var plan = preview(sdr(3840, 2160, rate)).prepare(params);
		assertEquals(rate, plan.frameRate());
		assertEquals(rate.toString(), value(plan.command(), "-r"));
		assertEquals(rate.nominalGopFrames(), plan.gopFrames());
		assertEquals(40000, plan.vbvBufferKbits());
		assertEquals("main10", value(plan.command(), "-profile:v"));
		assertTrue(value(plan.command(), "-x265-params").contains("keyint=" + rate.nominalGopFrames() + ":"));
		assertTrue(value(plan.command(), "-x265-params").contains("uhd-bd=1:level-idc=5.1:high-tier=1:open-gop=0"));
		assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("experimental")));
	}

	@Test
	void ffmpegRetimesAtInputWithoutDroppingOrDuplicatingFrames() throws IOException {
		var params = request(directory);
		params.setFrameRate(FrameRate.FPS_23976);
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals(FrameRate.FPS_24, plan.sourceFrameRate());
		assertEquals(1.001, plan.durationRatio(), 1e-12);
		assertEquals("24000/1001", value(plan.command(), "-r"));
		assertTrue(plan.command().indexOf("-r") < plan.command().indexOf("-i"));
		assertEquals(1, plan.command().stream().filter("-r"::equals).count());
		assertFalse(value(plan.command(), "-vf").contains("fps="));
		assertEquals("0", value(plan.command(), "-vsync"));
		assertEquals("1001/24000", value(plan.command(), "-enc_time_base"));
		assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("audio")));
	}

	@Test
	void missingTimingWarnsButSourceOverrideIsSeparateFromTarget() throws IOException {
		var params = request(directory);
		var converter = preview(sdr(1920, 1080, null));
		var fallback = converter.prepare(params);
		assertEquals(FrameRate.FPS_23976, fallback.frameRate());
		assertTrue(fallback.warnings().stream().anyMatch(warning -> warning.contains("fallback")));
		params.setSourceFrameRate(FrameRate.FPS_24);
		var overridden = converter.prepare(params);
		assertEquals(FrameRate.FPS_24, overridden.frameRate());
		assertTrue(overridden.warnings().isEmpty());
		params.setFrameRate(FrameRate.FPS_23976);
		assertEquals(1.001, converter.prepare(params).durationRatio(), 1e-12);
	}

	@ParameterizedTest
	@EnumSource(value = Engine.class, names = "FFMPEG", mode = EnumSource.Mode.EXCLUDE)
	void hardwareRejectsRetimingMissingTimingAndConflictingSourceOverride(Engine engine) throws IOException {
		var params = request(directory);
		params.setEngine(engine);
		params.setFrameRate(FrameRate.FPS_23976);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
		params.setFrameRate(null);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, null)).prepare(params));
		params.setSourceFrameRate(FrameRate.FPS_23976);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params));
		params.setSourceFrameRate(FrameRate.FPS_24);
		assertThrows(IllegalArgumentException.class, () -> preview(sdr(1920, 1080, null)).prepare(params));
	}

	@ParameterizedTest
	@EnumSource(value = Engine.class, names = "FFMPEG", mode = EnumSource.Mode.EXCLUDE)
	void hardwarePaddingUsesNativeCommandOnlyAndSurfacesWarnings(Engine engine) throws IOException {
		var params = request(directory);
		params.setEngine(engine);
		var plan = preview(sdr(1920, 900, FrameRate.FPS_24)).prepare(params);
		assertEquals("0,90,0,90", value(plan.command(), "--vpp-pad"));
		assertEquals("raw", value(plan.command(), "--output-format"));
		assertTrue(plan.command().contains("--avsw"));
		assertFalse(plan.command().contains("--fps"));
		assertEquals("1920x1080", value(plan.command(), "--output-res"));
		assertEquals("24", value(plan.command(), "--gop-len"));
		assertEquals("40000", value(plan.command(), "--max-bitrate"));
		assertFalse(plan.warnings().isEmpty());
		if (engine == Engine.VCEENCC) {
			assertTrue(plan.command().contains("--enforce-hrd"));
			assertFalse(plan.command().contains("--bluray"));
		} else {
			assertTrue(plan.command().contains("--bluray"));
		}
	}

	@Test
	void hdr10StaticMetadataIsPreservedInHevcCommandsAndRejectedForAvc() throws IOException {
		String mastering = "G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,50)";
		var hdr = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", "bt2020", "smpte2084",
				"bt2020nc", false, mastering, "1000,400");
		var params = request(directory);
		assertThrows(IllegalArgumentException.class, () -> preview(hdr).prepare(params));
		params.setCodec(H265_HEVC);
		var plan = preview(hdr).prepare(params);
		assertEquals("yuv420p10le", value(plan.command(), "-pix_fmt"));
		assertEquals("bt2020", value(plan.command(), "-color_primaries"));
		assertEquals("smpte2084", value(plan.command(), "-color_trc"));
		assertEquals("bt2020nc", value(plan.command(), "-colorspace"));
		String options = value(plan.command(), "-x265-params");
		assertTrue(options.contains(":master-display=" + mastering));
		assertTrue(options.contains(":max-cll=1000,400:hdr10=1"));
		for (Engine engine : List.of(Engine.NVENCC, Engine.VCEENCC, Engine.QSVENCC)) {
			params.setEngine(engine);
			var hardware = preview(hdr).prepare(params);
			assertEquals(mastering, value(hardware.command(), "--master-display"));
			assertEquals("1000,400", value(hardware.command(), "--max-cll"));
			assertEquals("10", value(hardware.command(), "--output-depth"));
			assertTrue(hardware.warnings().stream().anyMatch(warning -> warning.contains("UHD Blu-ray")));
		}
	}

	@Test
	void missingSdrMetadataWarnsAndMissingHdrMetadataIsNeverInvented() throws IOException {
		var params = request(directory);
		var missing = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "h264", null, null, null, false,
				null, null);
		var plan = preview(missing).prepare(params);
		assertEquals("bt709", value(plan.command(), "-color_primaries"));
		assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("Missing SDR")));
		params.setCodec(H265_HEVC);
		var hdr = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", "bt2020", "smpte2084",
				"bt2020nc", false, null, null);
		plan = preview(hdr).prepare(params);
		assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("mastering-display")));
		assertFalse(value(plan.command(), "-x265-params").contains("master-display="));
		var incomplete = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", null, "smpte2084",
				"bt2020nc", false, null, null);
		assertThrows(IllegalArgumentException.class, () -> preview(incomplete).prepare(params));
		var wrongMatrix = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "hevc", "bt2020", "smpte2084",
				"bt709", false, null, null);
		assertThrows(IllegalArgumentException.class, () -> preview(wrongMatrix).prepare(params));
	}

	@Test
	void fullRangePixelsAreConvertedRatherThanOnlyRelabeled() throws IOException {
		var params = request(directory);
		var fullRange = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "h264", "bt709", "bt709", "bt709",
				true, null, null);
		var plan = preview(fullRange).prepare(params);
		assertTrue(value(plan.command(), "-vf").contains("in_range=pc:out_range=tv"));
		assertEquals("tv", value(plan.command(), "-color_range"));
		params.setEngine(Engine.NVENCC);
		plan = preview(fullRange).prepare(params);
		assertEquals("range=full:limited", value(plan.command(), "--vpp-colorspace"));
	}

	@ParameterizedTest
	@EnumSource(value = Engine.class, names = "FFMPEG", mode = EnumSource.Mode.EXCLUDE)
	void hardwareExplicitResizeWarnsAndAnamorphicResizeFails(Engine engine) throws IOException {
		var params = request(directory);
		params.setEngine(engine);
		params.setWidth(1920);
		params.setHeight(1080);
		var plan = preview(sdr(640, 480, FrameRate.FPS_24)).prepare(params);
		assertEquals(1440, plan.contentWidth());
		assertEquals(1080, plan.contentHeight());
		assertEquals("1920x1080", value(plan.command(), "--output-res"));
		assertEquals("240,0,240,0", value(plan.command(), "--vpp-pad"));
		assertTrue(plan.warnings().stream().anyMatch(warning -> warning.contains("VPP")));
		var anamorphic = new VideoStreamInfo(1440, 1080, FrameRate.FPS_24, 4, 3, true, "h264", "bt709", "bt709",
				"bt709", false, null, null);
		assertThrows(IllegalArgumentException.class, () -> preview(anamorphic).prepare(params));
	}

	@ParameterizedTest
	@EnumSource(value = Engine.class, names = "FFMPEG", mode = EnumSource.Mode.EXCLUDE)
	void hardwareHorizontalPadOnlyStillEmitsFinalCanvasResolution(Engine engine) throws IOException {
		var params = request(directory);
		params.setEngine(engine);
		var plan = preview(sdr(1600, 1080, FrameRate.FPS_24)).prepare(params);
		assertEquals(1600, plan.contentWidth());
		assertEquals(1080, plan.contentHeight());
		assertEquals("1920x1080", value(plan.command(), "--output-res"));
		assertEquals("160,0,160,0", value(plan.command(), "--vpp-pad"));
		assertEquals(1, plan.command().stream().filter("--output-res"::equals).count());
	}

	@ParameterizedTest
	@EnumSource(value = Engine.class, names = "FFMPEG", mode = EnumSource.Mode.EXCLUDE)
	void hardwareUnchangedCanvasDoesNotRequestResizeOrPadding(Engine engine) throws IOException {
		var params = request(directory);
		params.setEngine(engine);
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		assertFalse(plan.command().contains("--output-res"));
		assertFalse(plan.command().contains("--vpp-pad"));
	}

	@Test
	void injectedConfigIsReadAtRuntimeAndRequestOverridesWin() throws IOException {
		Map<String, String> config = new HashMap<>();
		config.put(VideoConverter.ENGINE_PROPERTY, "qsvEncc");
		config.put(VideoConverter.CONFIG_PREFIX + "qsvencc.binary", "/configured/QSVEncC");
		List<String> lookups = new ArrayList<>();
		var converter = new VideoConverter((key, fallback) -> {
			lookups.add(key);
			return config.getOrDefault(key, fallback);
		}, path -> sdr(1920, 1080, FrameRate.FPS_24), new EncoderProcessRunner(command -> {
			throw new AssertionError("No launch during config tests");
		}));
		var params = request(directory);
		var configured = converter.prepare(params);
		assertEquals(Engine.QSVENCC, configured.engine());
		assertEquals("/configured/QSVEncC", configured.command().getFirst());
		assertEquals(List.of(VideoConverter.ENGINE_PROPERTY, VideoConverter.CONFIG_PREFIX + "qsvencc.binary"), lookups);
		config.put(VideoConverter.ENGINE_PROPERTY, "ffmpeg");
		config.put(VideoConverter.CONFIG_PREFIX + "ffmpeg.binary", "/changed/ffmpeg");
		assertEquals("/changed/ffmpeg", converter.prepare(params).command().getFirst());
		params.setEngine(Engine.NVENCC);
		params.setExecutablePath("/request/NVEncC with spaces");
		lookups.clear();
		assertEquals("/request/NVEncC with spaces", converter.prepare(params).command().getFirst());
		assertTrue(lookups.isEmpty());
		config.clear();
		params.setEngine(null);
		params.setExecutablePath(null);
		assertEquals("/usr/bin/ffmpeg", converter.prepare(params).command().getFirst());
	}

	@Test
	void preparedPlanSnapshotsMutableOptionsAndWarnings() throws IOException {
		var params = request(directory);
		params.getEngineOptions().put("preset", "slow");
		var plan = preview(sdr(1920, 1080, FrameRate.FPS_24)).prepare(params);
		params.getEngineOptions().put("preset", "fast");
		params.setBitrateKbps(1000);
		assertEquals("slow", value(plan.command(), "-preset"));
		assertEquals("20000k", value(plan.command(), "-b:v"));
		assertThrows(UnsupportedOperationException.class, () -> plan.command().clear());
		assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add("mutated"));
		assertEquals(RateControl.VBR, plan.rateControl());
	}

	@Test
	void invalidOptionsAndRateControlFailBeforeProbe() throws IOException {
		AtomicInteger probes = new AtomicInteger();
		var converter = new VideoConverter((key, fallback) -> fallback, path -> {
			probes.incrementAndGet();
			throw new AssertionError("Invalid request must be rejected before probing");
		}, new EncoderProcessRunner(command -> {
			throw new AssertionError("Invalid request must not launch");
		}));
		var params = request(directory);
		params.setBitrateKbps(null);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		params.setBitrateKbps(20000);
		params.getEngineOptions().put("x264-params", "keyint=999");
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		params.getEngineOptions().clear();
		params.getEngineOptions().put("preset", null);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		params.getEngineOptions().clear();
		params.getEngineOptions().put(null, "slow");
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		assertEquals(0, probes.get());
	}

	@Test
	void requestDefaultsAndInvalidCoreFieldsAreValidated() throws IOException {
		var converter = preview(sdr(1920, 1080, FrameRate.FPS_24));
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(null));
		var params = request(directory);
		params.setInputPath(null);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(params));
		var valid = request(directory);
		valid.setOutputPath(null);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(valid));
		valid.setOutputPath(directory.resolve("out.264"));
		valid.setTimeout(Duration.ZERO);
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(valid));
		valid.setTimeout(Duration.ofSeconds(-1));
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(valid));
		valid.setTimeout(null);
		valid.setCodec(null);
		valid.setEngineOptions(null);
		assertEquals(RateControl.VBR, converter.prepare(valid).rateControl());
		valid.setExecutablePath(" ");
		assertThrows(IllegalArgumentException.class, () -> converter.prepare(valid));
		assertEquals(Engine.FFMPEG, Engine.parse(" fFmPeG "));
		assertThrows(IllegalArgumentException.class, () -> Engine.parse("automatic"));
		assertThrows(IllegalArgumentException.class, () -> Engine.parse(null));
	}

	@Test
	void rejectsInterlacedAndNonElementaryStreamSources() throws IOException {
		var params = request(directory);
		var interlaced = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, false, "h264", "bt709", "bt709",
				"bt709", false, null, null);
		assertThrows(IllegalArgumentException.class, () -> preview(interlaced).prepare(params));
		var container = new VideoStreamInfo(1920, 1080, FrameRate.FPS_24, 1, 1, true, "matroska", "bt709", "bt709",
				"bt709", false, null, null);
		assertThrows(IllegalArgumentException.class, () -> preview(container).prepare(params));
	}
}
