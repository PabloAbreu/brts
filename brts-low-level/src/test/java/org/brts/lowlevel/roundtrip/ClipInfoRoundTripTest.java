package org.brts.lowlevel.roundtrip;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/roundtrip/ClipInfoRoundTripTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;

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
