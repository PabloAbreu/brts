/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/roundtrip/ClipInfoExtendedRoundTripTest.java' is part of BRTS.
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

import org.brts.common.model.StreamCodingType;
import org.brts.common.exception.ParseException;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

/**
 * Extended ClipInfo round-trip tests covering multiple codec types, PG/IG streams, timing boundaries, and application
 * types.
 */
class ClipInfoExtendedRoundTripTest {

	private final ClipInfoWriter writer = new ClipInfoWriter();

	private final ClipInfoParser parser = new ClipInfoParser();

	// -------------------------------------------------------------------------
	// Codec coverage
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_hevcVideo_codecPreserved() throws Exception {
		ClipInfo ci = build(StreamCodingType.H265_HEVC, 0x06, 0x07, 0x03, null, null, null, null);
		ClipInfo out = roundTrip(ci);

		ClipStream v = videoStream(out);
		assertThat(v.getCodingType()).isEqualTo(StreamCodingType.H265_HEVC);
		assertThat(v.getVideoFormat()).isEqualTo(0x06);
		assertThat(v.getFrameRate()).isEqualTo(0x07); // 60000/1001
		assertThat(v.getAspectRatio()).isEqualTo(0x03); // 16:9
	}

	@Test
	void roundTrip_dtsHdMasterAudio_codecAndLanguagePreserved() throws Exception {
		ClipInfo ci = build(StreamCodingType.H264_AVC, 0x06, 0x04, 0x03, StreamCodingType.DTS_HD_MASTER_AUDIO, 0x0C,
				0x04, "jpn");
		ClipInfo out = roundTrip(ci);

		ClipStream a = audioStream(out);
		assertThat(a.getCodingType()).isEqualTo(StreamCodingType.DTS_HD_MASTER_AUDIO);
		assertThat(a.getAudioChannelLayout()).isEqualTo(0x0C);
		assertThat(a.getSampleRate()).isEqualTo(0x04); // 96 kHz
		assertThat(a.getLanguage()).isEqualTo("jpn");
	}

	@Test
	void roundTrip_ac3PlusAudio_preserved() throws Exception {
		ClipInfo ci = build(StreamCodingType.H264_AVC, 0x06, 0x04, 0x03, StreamCodingType.DOLBY_AC3_PLUS, 0x06, 0x01,
				"deu");
		ClipInfo out = roundTrip(ci);

		ClipStream a = audioStream(out);
		assertThat(a.getCodingType()).isEqualTo(StreamCodingType.DOLBY_AC3_PLUS);
		assertThat(a.getLanguage()).isEqualTo("deu");
	}

	// -------------------------------------------------------------------------
	// PG / IG streams
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_presentationGraphicsStream_languagePreserved() throws Exception {
		ClipInfo ci = baseClip();
		ClipStream pg = new ClipStream();
		pg.setPid(0x1200);
		pg.setCodingType(StreamCodingType.PRESENTATION_GRAPHICS);
		pg.setLanguage("fra");
		ci.setStreams(List.of(videoStreamOf(StreamCodingType.H264_AVC), pg));

		ClipInfo out = roundTrip(ci);

		ClipStream pgOut = out.getStreams().stream()
				.filter(s -> s.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS).findFirst().orElseThrow();
		assertThat(pgOut.getPid()).isEqualTo(0x1200);
		assertThat(pgOut.getLanguage()).isEqualTo("fra");
	}

	@Test
	void roundTrip_interactiveGraphicsStream_languagePreserved() throws Exception {
		ClipInfo ci = baseClip();
		ClipStream ig = new ClipStream();
		ig.setPid(0x1400);
		ig.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
		ig.setLanguage("eng");
		ci.setStreams(List.of(videoStreamOf(StreamCodingType.H264_AVC), ig));

		ClipInfo out = roundTrip(ci);

		ClipStream igOut = out.getStreams().stream()
				.filter(s -> s.getCodingType() == StreamCodingType.INTERACTIVE_GRAPHICS).findFirst().orElseThrow();
		assertThat(igOut.getPid()).isEqualTo(0x1400);
		assertThat(igOut.getLanguage()).isEqualTo("eng");
	}

	// -------------------------------------------------------------------------
	// Timing
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_longDuration_timingWithinOneTick() throws Exception {
		ClipInfo ci = baseClip();
		// 2 hours in 90 kHz ticks = 2 * 3600 * 90000 = 648_000_000
		ci.setTsRecordingStartPts(Timestamp.ofTicks(0));
		ci.setTsRecordingEndPts(Timestamp.ofTicks(648_000_000L));
		ci.setDuration(Timestamp.ofTicks(648_000_000L));

		ClipInfo out = roundTrip(ci);

		// 45↔90 kHz conversion: max rounding error is 2 ticks
		assertThat(out.getDuration().getTicks()).isCloseTo(648_000_000L, offset(2L));
		assertThat(out.getTsRecordingEndPts().getTicks()).isCloseTo(648_000_000L, offset(2L));
	}

	@Test
	void roundTrip_nonZeroStartPts_preserved() throws Exception {
		ClipInfo ci = baseClip();
		// Non-zero start (e.g. concat segment starting at 30 min)
		long startTicks = 162_000_000L; // 30 min * 90000 ticks/s
		long endTicks = startTicks + 90_000L * 5400; // + 90 min
		ci.setTsRecordingStartPts(Timestamp.ofTicks(startTicks));
		ci.setTsRecordingEndPts(Timestamp.ofTicks(endTicks));
		ci.setDuration(Timestamp.ofTicks(endTicks - startTicks));

		ClipInfo out = roundTrip(ci);

		assertThat(out.getTsRecordingStartPts().getTicks()).isCloseTo(startTicks, offset(2L));
		assertThat(out.getTsRecordingEndPts().getTicks()).isCloseTo(endTicks, offset(2L));
	}

	// -------------------------------------------------------------------------
	// Application type
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_menuClip_applicationTypePreserved() throws Exception {
		ClipInfo ci = baseClip();
		ci.setApplicationType(3); // Interactive menu
		ci.setStreams(List.of(videoStreamOf(StreamCodingType.H264_AVC)));

		ClipInfo out = roundTrip(ci);

		assertThat(out.getApplicationType()).isEqualTo(3);
	}

	// -------------------------------------------------------------------------
	// Stream counts
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_multipleAudioTracks_allPreserved() throws Exception {
		ClipInfo ci = baseClip();
		ClipStream audio1 = audioStreamOf(StreamCodingType.DOLBY_TRUEHD, 0x1100, "eng");
		ClipStream audio2 = audioStreamOf(StreamCodingType.DOLBY_AC3, 0x1101, "fra");
		ClipStream audio3 = audioStreamOf(StreamCodingType.DTS, 0x1102, "deu");
		ci.setStreams(List.of(videoStreamOf(StreamCodingType.H264_AVC), audio1, audio2, audio3));

		ClipInfo out = roundTrip(ci);

		assertThat(out.getStreams()).hasSize(4);
		long audioCount = out.getStreams().stream().filter(s -> s.getCodingType().isAudio()).count();
		assertThat(audioCount).isEqualTo(3);
		assertThat(
				out.getStreams().stream().filter(s -> s.getLanguage() != null && s.getLanguage().equals("fra")).count())
				.isEqualTo(1);
	}

	@Test
	void version0300HevcWriterUsesSpecifiedBitLayoutAndKeepsFollowingStreamsAligned() throws Exception {
		ClipInfo ci = hevc300Clip();
		ClipStream video = ci.getStreams().getFirst();
		video.setOcFlag(true);
		video.setCrFlag(true);
		video.setDynamicRangeType(10);
		video.setColorSpace(5);
		video.setHdrPlusFlag(true);
		ClipStream pg = new ClipStream();
		pg.setPid(0x1200);
		pg.setCodingType(StreamCodingType.PRESENTATION_GRAPHICS);
		pg.setLanguage("fra");
		ClipStream ig = new ClipStream();
		ig.setPid(0x1400);
		ig.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
		ig.setLanguage("eng");
		ci.setStreams(List.of(video, audioStreamOf(StreamCodingType.DOLBY_AC3, 0x1100, "jpn"), pg, ig));
		byte[] bytes = writeBytes(ci);
		int attributes = firstAttributeOffset(bytes);
		assertThat(bytes[attributes - 1] & 0xFF).isEqualTo(21);
		// coding_type, format/rate, aspect/OC/CR, dynamic_range/color_space, HDR+.
		assertThat(Arrays.copyOfRange(bytes, attributes, attributes + 5))
				.isEqualTo(new byte[] { 0x24, (byte) 0x87, 0x33, (byte) 0xA5, (byte) 0x80 });
		int programStart = ByteBuffer.wrap(bytes).getInt(12);
		int cpiStart = ByteBuffer.wrap(bytes).getInt(16);
		assertThat(cpiStart - programStart).isEqualTo(112); // 14-byte header + 4 * 24-byte streams, aligned
		ClipInfo parsed = parser.parse(new ByteArrayInputStream(bytes));
		assertThat(parsed.getStreams()).usingRecursiveComparison().isEqualTo(ci.getStreams());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
	void version0300HevcParserReadsIndependentFlagBytes(int flags) throws Exception {
		// Start from legacy output, then hand-author 0300 metadata independently of the new writer path.
		ClipInfo ci = hevc300Clip();
		ci.setVersion("0200");
		byte[] bytes = writeBytes(ci);
		System.arraycopy("0300".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
		int attributes = firstAttributeOffset(bytes);
		bytes[attributes + 2] = (byte) (0x30 | ((flags & 1) != 0 ? 2 : 0) | ((flags & 2) != 0 ? 1 : 0));
		bytes[attributes + 3] = (byte) 0xC7;
		bytes[attributes + 4] = (byte) ((flags & 4) != 0 ? 0x80 : 0);
		ClipInfo parsed = parser.parse(new ByteArrayInputStream(bytes));
		ClipStream video = parsed.getStreams().getFirst();
		assertThat(video.getOcFlag()).isEqualTo((flags & 1) != 0);
		assertThat(video.getCrFlag()).isEqualTo((flags & 2) != 0);
		assertThat(video.getDynamicRangeType()).isEqualTo(12);
		assertThat(video.getColorSpace()).isEqualTo(7);
		assertThat(video.getHdrPlusFlag()).isEqualTo((flags & 4) != 0);
		assertThat(writeBytes(parsed)).isEqualTo(bytes);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 15 })
	void version0300HevcNibbleBoundaries(int value) throws Exception {
		ClipInfo ci = hevc300Clip();
		ci.getStreams().getFirst().setDynamicRangeType(value);
		ci.getStreams().getFirst().setColorSpace(value);
		ClipStream parsed = roundTrip(ci).getStreams().getFirst();
		assertThat(parsed.getDynamicRangeType()).isEqualTo(value);
		assertThat(parsed.getColorSpace()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 16 })
	void version0300HevcRejectsOutOfRangeNibblesBeforeOutput(int value) {
		for (String field : List.of("dynamicRangeType", "colorSpace")) {
			ClipInfo ci = hevc300Clip();
			if (field.equals("dynamicRangeType")) {
				ci.getStreams().getFirst().setDynamicRangeType(value);
			} else {
				ci.getStreams().getFirst().setColorSpace(value);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			assertThatThrownBy(() -> writer.write(ci, out)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(field).hasMessageContaining("PID 0x1011").hasMessageContaining("0 and 15");
			assertThat(out.size()).isZero();
		}
	}

	@Test
	void version0300HevcNullAttributesEncodeAsZero() throws Exception {
		ClipInfo ci = hevc300Clip();
		byte[] bytes = writeBytes(ci);
		int attributes = firstAttributeOffset(bytes);
		assertThat(Arrays.copyOfRange(bytes, attributes, attributes + 5))
				.isEqualTo(new byte[] { 0x24, (byte) 0x87, 0x30, 0, 0 });
		ClipStream parsed = roundTrip(ci).getStreams().getFirst();
		assertThat(parsed.getOcFlag()).isFalse();
		assertThat(parsed.getCrFlag()).isFalse();
		assertThat(parsed.getDynamicRangeType()).isZero();
		assertThat(parsed.getColorSpace()).isZero();
		assertThat(parsed.getHdrPlusFlag()).isFalse();
	}

	@Test
	void version0300NonHevcPreservesOnlyOcFlag() throws Exception {
		ClipInfo ci = hevc300Clip();
		ClipStream video = ci.getStreams().getFirst();
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setOcFlag(true);
		video.setCrFlag(true);
		video.setDynamicRangeType(15);
		video.setColorSpace(15);
		video.setHdrPlusFlag(true);
		byte[] bytes = writeBytes(ci);
		int attributes = firstAttributeOffset(bytes);
		assertThat(Arrays.copyOfRange(bytes, attributes, attributes + 5))
				.isEqualTo(new byte[] { 0x1B, (byte) 0x87, 0x32, 0, 0 });
		ClipStream parsed = roundTrip(ci).getStreams().getFirst();
		assertThat(parsed.getOcFlag()).isTrue();
		assertThat(parsed.getCrFlag()).isNull();
		assertThat(parsed.getDynamicRangeType()).isNull();
		assertThat(parsed.getColorSpace()).isNull();
		assertThat(parsed.getHdrPlusFlag()).isNull();
	}

	@Test
	void version0300UndersizedVideoAttributesFailWithContext() throws Exception {
		byte[] bytes = writeBytes(hevc300Clip());
		bytes[firstAttributeOffset(bytes) - 1] = 4;
		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(bytes))).isInstanceOf(ParseException.class)
				.hasMessageContaining("0300").hasMessageContaining("PID 0x1011").hasMessageContaining("offset")
				.hasMessageContaining("length 4");
	}

	@Test
	void version0300TruncatedVideoAttributesFail() throws Exception {
		byte[] bytes = writeBytes(hevc300Clip());
		byte[] truncated = Arrays.copyOf(bytes, firstAttributeOffset(bytes) + 4);
		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(truncated))).isInstanceOf(EOFException.class)
				.hasMessageContaining("position");
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private ClipInfo hevc300Clip() {
		ClipInfo ci = build(StreamCodingType.H265_HEVC, 0x08, 0x07, 0x03, null, null, null, null);
		ci.setVersion("0300");
		return ci;
	}

	private byte[] writeBytes(ClipInfo ci) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(ci, out);
		return out.toByteArray();
	}

	private int firstAttributeOffset(byte[] bytes) {
		// ProgramInfo: length(4), reserved/count(2), program header(8), PID(2), attribute length(1).
		return ByteBuffer.wrap(bytes).getInt(12) + 17;
	}

	private ClipInfo roundTrip(ClipInfo ci) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(ci, out);
		return parser.parse(new ByteArrayInputStream(out.toByteArray()));
	}

	private ClipInfo baseClip() {
		ClipInfo ci = new ClipInfo();
		ci.setClipName("00001");
		ci.setClipStreamType(1);
		ci.setApplicationType(1);
		ci.setTsRecordingStartPts(Timestamp.ofTicks(0));
		ci.setTsRecordingEndPts(Timestamp.ofTicks(8_100_000L));
		ci.setDuration(Timestamp.ofTicks(8_100_000L));
		return ci;
	}

	/** Builds a ClipInfo with one video + one audio stream. */
	private ClipInfo build(StreamCodingType videoCodec, int vFormat, int vFrameRate, int vAspect,
			StreamCodingType audioCodec, Integer aCh, Integer aSr, String aLang) {
		ClipInfo ci = baseClip();
		List<ClipStream> streams = new java.util.ArrayList<>();
		streams.add(videoStreamWith(videoCodec, vFormat, vFrameRate, vAspect));
		if (audioCodec != null)
			streams.add(audioStreamOf(audioCodec, 0x1100, aCh, aSr, aLang));
		ci.setStreams(streams);
		return ci;
	}

	private ClipStream videoStreamOf(StreamCodingType codec) {
		return videoStreamWith(codec, 0x06, 0x04, 0x03);
	}

	private ClipStream videoStreamWith(StreamCodingType codec, int vf, int fr, int ar) {
		ClipStream s = new ClipStream();
		s.setPid(0x1011);
		s.setCodingType(codec);
		s.setVideoFormat(vf);
		s.setFrameRate(fr);
		s.setAspectRatio(ar);
		return s;
	}

	private ClipStream audioStreamOf(StreamCodingType codec, int pid, String lang) {
		return audioStreamOf(codec, pid, 0x06, 0x01, lang);
	}

	private ClipStream audioStreamOf(StreamCodingType codec, int pid, int ch, int sr, String lang) {
		ClipStream s = new ClipStream();
		s.setPid(pid);
		s.setCodingType(codec);
		s.setAudioChannelLayout(ch);
		s.setSampleRate(sr);
		s.setLanguage(lang);
		return s;
	}

	private ClipStream videoStream(ClipInfo ci) {
		return ci.getStreams().stream().filter(s -> s.getCodingType().isVideo()).findFirst().orElseThrow();
	}

	private ClipStream audioStream(ClipInfo ci) {
		return ci.getStreams().stream().filter(s -> s.getCodingType().isAudio()).findFirst().orElseThrow();
	}

}
