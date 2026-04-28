package org.brts.lowlevel.roundtrip;

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
