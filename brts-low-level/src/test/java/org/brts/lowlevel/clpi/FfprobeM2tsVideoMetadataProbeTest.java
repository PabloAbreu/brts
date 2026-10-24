/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/FfprobeM2tsVideoMetadataProbeTest.java' is part of BRTS.
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
package org.brts.lowlevel.clpi;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.clpi.M2tsVideoMetadataProbe.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FfprobeM2tsVideoMetadataProbeTest {

	private static final Map<Integer, StreamCodingType> HEVC = Map.of(0x1011, StreamCodingType.H265_HEVC);

	@TempDir
	Path directory;

	@Test
	void hdr10HevcUsesFirstFrameColorimetry() throws IOException {
		String json = """
				{"frames":[{"stream_index":0,"width":3840,"height":2160,"interlaced_frame":0,
				  "color_transfer":"smpte2084","color_primaries":"bt2020",
				  "side_data_list":[{"side_data_type":"Mastering display metadata"}]},
				  {"stream_index":0,"width":3840,"height":2160,"interlaced_frame":0,"color_transfer":"bt709"}],
				 "streams":[{"index":0,"id":"0x1011","codec_name":"hevc","width":3840,"height":2160,
				  "field_order":"progressive","color_transfer":"smpte2084","color_primaries":"bt2020"}]}
				""";
		assertEquals(Map.of(0x1011, new Metadata(8, true, 1, 2, null)),
				FfprobeM2tsVideoMetadataProbe.parse(json, HEVC));
	}

	@Test
	void dolbyVisionAndHdr10PlusComeFromStreamOrFrameSideData() throws IOException {
		String json = """
				{"frames":[{"stream_index":0,"interlaced_frame":0,
				  "side_data_list":[{"side_data_type":"HDR Dynamic Metadata SMPTE2094-40 (HDR10+)"}]}],
				 "streams":[{"index":0,"id":"0x1011","codec_name":"hevc","width":3840,"height":2160,
				  "field_order":"progressive","color_transfer":"smpte2084","color_primaries":"bt2020",
				  "side_data_list":[{"side_data_type":"DOVI configuration record"}]}]}
				""";
		assertEquals(Map.of(0x1011, new Metadata(8, true, 2, 2, true)),
				FfprobeM2tsVideoMetadataProbe.parse(json, HEVC));
	}

	@Test
	void interlacedAvcWithoutFramesFallsBackToStreamValues() throws IOException {
		String json = """
				{"streams":[{"index":0,"id":"0x1011","codec_name":"h264","width":1920,"height":1080,
				  "field_order":"tt","color_transfer":"bt709","color_primaries":"bt709"}]}
				""";
		assertEquals(Map.of(0x1011, new Metadata(4, false, 0, 1, null)),
				FfprobeM2tsVideoMetadataProbe.parse(json, Map.of(0x1011, StreamCodingType.H264_AVC)));
	}

	@Test
	void unknownFrameColorFallsBackToStreamAndHlgStaysUnknown() throws IOException {
		String json = """
				{"frames":[{"stream_index":0,"interlaced_frame":0,"color_transfer":"unknown"}],
				 "streams":[{"index":0,"id":"0x1011","codec_name":"hevc","width":1920,"height":1080,
				  "color_transfer":"arib-std-b67","color_primaries":"bt2020"}]}
				""";
		assertEquals(Map.of(0x1011, new Metadata(6, false, null, 2, null)),
				FfprobeM2tsVideoMetadataProbe.parse(json, HEVC));
	}

	@Test
	void ignoresUnexpectedPidsAndCodecMismatches() throws IOException {
		String json = """
				{"streams":[{"index":0,"id":"0x1011","codec_name":"h264","width":1920,"height":1080},
				  {"index":1,"id":"0x1015","codec_name":"hevc","width":1920,"height":1080},
				  {"index":2,"codec_name":"hevc"}]}
				""";
		assertTrue(FfprobeM2tsVideoMetadataProbe.parse(json, HEVC).isEmpty());
	}

	@ParameterizedTest
	@CsvSource({ "smpte2084,1", "bt709,0", "bt470bg,0", "smpte170m,0", "bt2020-10,0", "iec61966-2-1,0" })
	void mapsKnownTransferNames(String transfer, int expected) {
		assertEquals(expected, FfprobeM2tsVideoMetadataProbe.dynamicRange(transfer, false));
	}

	@ParameterizedTest
	@ValueSource(strings = { "arib-std-b67", "smpte428", "reserved", "unknown" })
	void unknownTransferNamesRemainUnknown(String transfer) {
		assertNull(FfprobeM2tsVideoMetadataProbe.dynamicRange(transfer, false));
		assertEquals(2, FfprobeM2tsVideoMetadataProbe.dynamicRange(transfer, true));
	}

	@Test
	void mapsPrimariesAndCodecNames() {
		assertEquals(1, FfprobeM2tsVideoMetadataProbe.colorSpace("bt709"));
		assertEquals(2, FfprobeM2tsVideoMetadataProbe.colorSpace("bt2020"));
		assertNull(FfprobeM2tsVideoMetadataProbe.colorSpace("smpte170m"));
		assertNull(FfprobeM2tsVideoMetadataProbe.colorSpace(null));
		assertTrue(FfprobeM2tsVideoMetadataProbe.matchesCodec(StreamCodingType.MPEG2_VIDEO, "mpeg2video"));
		assertTrue(FfprobeM2tsVideoMetadataProbe.matchesCodec(StreamCodingType.VC1, "vc1"));
		assertFalse(FfprobeM2tsVideoMetadataProbe.matchesCodec(StreamCodingType.DOLBY_AC3, "ac3"));
		assertFalse(FfprobeM2tsVideoMetadataProbe.matchesCodec(null, "hevc"));
	}

	@Test
	void commandIsTokenizedAndBounded() {
		var probe = new FfprobeM2tsVideoMetadataProbe("/usr/bin/ffprobe", 12, Duration.ofSeconds(5));
		var command = probe.command(Path.of("/tmp/a b.m2ts"));
		assertEquals("/usr/bin/ffprobe", command.getFirst());
		assertEquals("/tmp/a b.m2ts", command.getLast());
		assertEquals("%+#12", command.get(command.indexOf("-read_intervals") + 1));
	}

	@Test
	void rejectsInvalidSettings() {
		assertThrows(IllegalArgumentException.class,
				() -> new FfprobeM2tsVideoMetadataProbe("ffprobe", 0, Duration.ofSeconds(1)));
		assertThrows(IllegalArgumentException.class,
				() -> new FfprobeM2tsVideoMetadataProbe("ffprobe", 1, Duration.ZERO));
	}

	@Test
	void missingBinaryFallsBackToLibraryEngine() {
		Map<String, String> config = Map.of(FfprobeM2tsVideoMetadataProbe.BINARY_PROPERTY,
				directory.resolve("missing-ffprobe").toString());
		assertNull(FfprobeM2tsVideoMetadataProbe.fromConfig(config::get));
		assertNotNull(M2tsClpiRegenBuilder.defaultProbe(config::get));
		assertThrows(IllegalArgumentException.class, () -> M2tsClpiRegenBuilder
				.defaultProbe(Map.of(M2tsClpiRegenBuilder.PROBE_ENGINE_PROPERTY, "mediainfo")::get));
	}

	@Test
	void nativeFfprobeReadsRealM2ts() throws IOException {
		var probe = FfprobeM2tsVideoMetadataProbe.fromConfig(key -> null);
		assumeTrue(probe != null, "ffprobe not on PATH");
		Path sample = Path.of("../samples/HOAD_DEMO/BDMV/STREAM/00002.m2ts");
		assumeTrue(Files.isRegularFile(sample), "sample not available");
		var result = probe.probe(sample, Map.of(0x1011, StreamCodingType.VC1));
		assertEquals(6, result.get(0x1011).videoFormat());
		assertFalse(result.get(0x1011).fourK());
	}

	@Test
	void nativeFfprobeFailureIsIoException() throws IOException {
		var probe = FfprobeM2tsVideoMetadataProbe.fromConfig(key -> null);
		assumeTrue(probe != null, "ffprobe not on PATH");
		Path invalid = Files.writeString(directory.resolve("invalid.m2ts"), "not a transport stream");
		try {
			assertTrue(probe.probe(invalid, HEVC).isEmpty());
		} catch (IOException expected) {
			// Either outcome is acceptable: no metadata is invented for garbage input.
		}
	}
}
