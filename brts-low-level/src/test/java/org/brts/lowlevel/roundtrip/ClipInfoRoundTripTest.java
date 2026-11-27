/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/roundtrip/ClipInfoRoundTripTest.java' is part of BRTS.
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

package org.brts.lowlevel.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Round-trip test: write a ClipInfo to binary, re-parse it, and verify field consistency.
 */
class ClipInfoRoundTripTest {

	private final ClipInfoWriter writer = new ClipInfoWriter();

	private final ClipInfoParser parser = new ClipInfoParser();

	@Test
	void roundTrip_basicClipInfo_fieldsAreConsistent() throws Exception {
		ClipInfo original = buildSampleClipInfo();

		// Write to bytes
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(original, out);
		byte[] bytes = out.toByteArray();

		// Re-parse from bytes
		ClipInfo reparsed = parser.parse(new ByteArrayInputStream(bytes));

		// Verify top-level fields
		assertThat(reparsed.getClipStreamType()).isEqualTo(original.getClipStreamType());
		assertThat(reparsed.getApplicationType()).isEqualTo(original.getApplicationType());

		// Verify duration (within 1 tick rounding due to 45↔90 kHz conversion)
		assertThat(reparsed.getDuration().getTicks()).isCloseTo(original.getDuration().getTicks(),
				org.assertj.core.data.Offset.offset(2L));

		// Verify streams
		assertThat(reparsed.getStreams()).hasSize(original.getStreams().size());
		ClipStream videoOut = reparsed.getStreams().stream().filter(s -> s.getCodingType().isVideo()).findFirst()
				.orElseThrow();
		assertThat(videoOut.getPid()).isEqualTo(0x1011);
		assertThat(videoOut.getCodingType()).isEqualTo(StreamCodingType.H264_AVC);

		ClipStream audioOut = reparsed.getStreams().stream().filter(s -> s.getCodingType().isAudio()).findFirst()
				.orElseThrow();
		assertThat(audioOut.getPid()).isEqualTo(0x1100);
		assertThat(audioOut.getLanguage()).isEqualTo("eng");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "0200", "0300" })
	void roundTrip_versionIsSelectedAndPreserved(String version) throws Exception {
		ClipInfo original = buildSampleClipInfo();
		assertThat(original.getVersion()).isEqualTo("0200");
		original.setVersion(version);
		byte[] bytes = write(original);
		String expected = version == null ? "0200" : version;
		assertThat(new String(bytes, 0, 8, StandardCharsets.US_ASCII)).isEqualTo("HDMV" + expected);
		assertThat(parser.parse(new ByteArrayInputStream(bytes)).getVersion()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0100", "0400", "", "300", "03000", " 0300" })
	void unsupportedWriteVersionIsRejectedBeforeOutput(String version) {
		ClipInfo original = buildSampleClipInfo();
		original.setVersion(version);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThatThrownBy(() -> writer.write(original, out)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CLPI write version").hasMessageContaining("0200 or 0300");
		assertThat(out.size()).isZero();
	}

	@Test
	void version0100ParsingRetainsLegacyFields() throws Exception {
		byte[] bytes = write(buildSampleClipInfo());
		ClipInfo expected = parser.parse(new ByteArrayInputStream(bytes));
		System.arraycopy("0100".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
		ClipInfo parsed = parser.parse(new ByteArrayInputStream(bytes));
		assertThat(parsed.getVersion()).isEqualTo("0100");
		assertThat(parsed).usingRecursiveComparison().ignoringFields("version").isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "H264_AVC", "H265_HEVC" })
	void default0200BytesMatchPreChangeGoldenHash(String codec) throws Exception {
		ClipInfo original = buildSampleClipInfo();
		ClipStream video = original.getStreams().getFirst();
		video.setCodingType(StreamCodingType.valueOf(codec));
		String expected = codec.equals("H264_AVC") ? "bc8dc1aa8fb45b8e946df238f58f915a286d8532599e2e596fdb485e1187114d"
				: "df50af8ee32c5c74db51df384b6b23f51aaf1231945bd0109e6fde2e19f91fb1";
		// Captured from the pre-0300 writer with this exact AVC/HEVC + AC3 model.
		assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(original))))
				.isEqualTo(expected);
		video.setOcFlag(true);
		video.setCrFlag(true);
		video.setDynamicRangeType(15);
		video.setColorSpace(15);
		video.setHdrPlusFlag(true);
		assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(original))))
				.isEqualTo(expected);
	}

	@Test
	void jsonVersionDefaultsAnd0300MetadataArePreserved() throws Exception {
		var mapper = JsonMapperFactory.get();
		assertThat(mapper.readValue("{}", ClipInfo.class).getVersion()).isEqualTo("0200");
		ClipInfo nullVersion = mapper.readValue("{\"version\":null}", ClipInfo.class);
		assertThat(parser.parse(new ByteArrayInputStream(write(nullVersion))).getVersion()).isEqualTo("0200");
		ClipInfo original = buildSampleClipInfo();
		original.setVersion("0300");
		ClipStream video = original.getStreams().getFirst();
		video.setCodingType(StreamCodingType.H265_HEVC);
		video.setOcFlag(true);
		video.setCrFlag(true);
		video.setDynamicRangeType(2);
		video.setColorSpace(6);
		video.setHdrPlusFlag(true);
		byte[] bytes = write(original);
		ClipInfo parsed = parser.parse(new ByteArrayInputStream(bytes));
		ClipInfo fromJson = mapper.readValue(mapper.writeValueAsString(parsed), ClipInfo.class);
		assertThat(fromJson).usingRecursiveComparison().isEqualTo(parsed);
		assertThat(write(fromJson)).isEqualTo(bytes);
	}

	private byte[] write(ClipInfo model) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(model, out);
		return out.toByteArray();
	}

	private ClipInfo buildSampleClipInfo() {
		ClipInfo ci = new ClipInfo();
		ci.setClipName("00001");
		ci.setClipStreamType(1);
		ci.setApplicationType(1);
		ci.setTsRecordingStartPts(Timestamp.ofSeconds(0));
		ci.setTsRecordingEndPts(Timestamp.ofSeconds(90));
		ci.setDuration(Timestamp.ofSeconds(90));

		ClipStream video = new ClipStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06); // 1080p
		video.setFrameRate(0x04); // 30000/1001
		video.setAspectRatio(0x03); // 16:9

		ClipStream audio = new ClipStream();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		audio.setAudioChannelLayout(0x06); // multi
		audio.setSampleRate(0x01); // 48kHz
		audio.setLanguage("eng");

		ci.setStreams(List.of(video, audio));
		ci.setEpMap(null);
		return ci;
	}

}
