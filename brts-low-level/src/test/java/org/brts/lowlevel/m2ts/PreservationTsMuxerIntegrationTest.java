/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservationTsMuxerIntegrationTest.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Optional real-tool regression. Set -Dbrts.test.tsmuxer to override the local executable. */
class PreservationTsMuxerIntegrationTest {
	@TempDir
	Path temp;

	@Test
	void actualBluRaySitExtendedAudioPesAndRepacketizedAc3() throws Exception {
		Path tsmuxer = Path.of(System.getProperty("brts.test.tsmuxer", "/data/pablo/tools/tsMuxer_2.8.14/tsMuxeR"));
		assumeTrue(Files.isExecutable(tsmuxer) && Files.isExecutable(Path.of("/usr/bin/ffmpeg")),
				"Local tsMuxeR and FFmpeg integration tools are unavailable");
		Path source = temp.resolve("source.m2ts");
		Process ffmpeg = new ProcessBuilder("/usr/bin/ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-f",
				"lavfi", "-i", "testsrc2=size=320x240:rate=25", "-f", "lavfi", "-i",
				"sine=frequency=440:sample_rate=48000", "-t", "0.64", "-c:v", "libx264", "-threads", "1", "-bf", "0",
				"-g", "12", "-c:a", "ac3", "-b:a", "192k", "-mpegts_m2ts_mode", "1", "-f", "mpegts", source.toString())
				.redirectErrorStream(true).redirectOutput(temp.resolve("ffmpeg.log").toFile()).start();
		try {
			assertThat(ffmpeg.waitFor(60, TimeUnit.SECONDS)).isTrue();
			assertThat(ffmpeg.exitValue()).as("FFmpeg fixture creation").isZero();
		} finally {
			ffmpeg.destroyForcibly();
		}
		var sourceInfo = new M2tsParser().parse(source);
		Path extracted = temp.resolve("source-es");
		new M2tsExtractor().extract(source, sourceInfo, extracted, null, true);
		M2tsTimingManifest sourceTiming = M2tsTiming.read(extracted.resolve(M2tsTimingManifest.FILE_NAME));
		List<M2tsDescriptor.StreamEntry> entries = sourceInfo.getStreams().stream().map(s -> {
			var entry = new M2tsDescriptor.StreamEntry();
			entry.setPid(s.getPid());
			entry.setStreamTypeByte(s.getStreamTypeByte());
			return entry;
		}).toList();
		var video = sourceTiming.streams().stream().filter(s -> s.codingType().isVideo()).findFirst().orElseThrow();
		var audio = sourceTiming.streams().stream().filter(s -> s.codingType() == StreamCodingType.DOLBY_AC3)
				.findFirst().orElseThrow();
		Path meta = temp.resolve("input.meta");
		Files.writeString(meta,
				"MUXOPT --blu-ray --new-audio-pes --vbr --vbv-len=500\n" + "V_MPEG4/ISO/AVC, \""
						+ extracted.resolve(video.fileName()) + "\", fps=25\n" + "A_AC3, \""
						+ extracted.resolve(audio.fileName()) + "\"\n");
		Path mux = temp.resolve("mux");
		Files.createDirectories(mux);
		PreservingM2tsClipWriter.run(tsmuxer.toString(), meta, mux, temp.resolve("tsmuxer.log"));
		Path generated = mux.resolve("BDMV/STREAM/00000.m2ts");
		var info = new M2tsParser().parse(generated);
		assertThat(info.getSitPid()).isEqualTo(0x1f);
		Map<Integer, Integer> mapping = PreservationTransportNormalizer.mapping(info, sourceInfo, entries);
		Path muxEs = temp.resolve("mux-es");
		new M2tsExtractor().extract(generated, info, muxEs, null, true);
		var muxTiming = M2tsTiming.read(muxEs.resolve(M2tsTimingManifest.FILE_NAME));
		var muxAudio = muxTiming.streams().stream().filter(s -> s.codingType() == StreamCodingType.DOLBY_AC3)
				.findFirst().orElseThrow();
		PreservationVideoTimeline.validate(extracted.resolve(video.fileName()), video);
		var muxVideo = muxTiming.streams().stream().filter(s -> s.codingType().isVideo()).findFirst().orElseThrow();
		PreservationVideoTimeline.validate(muxEs.resolve(muxVideo.fileName()), muxVideo);
		assertThat(muxAudio.pesTimings().size()).isNotEqualTo(audio.pesTimings().size());
		long shift = audio.firstPts90k() - muxAudio.firstPts90k();
		PreservationAc3Timeline.compare(extracted.resolve(audio.fileName()), audio, muxEs.resolve(muxAudio.fileName()),
				muxAudio, shift);
		byte[] bytes = Files.readAllBytes(generated);
		boolean sawExtendedAudio = false;
		for (int at = 0; at < bytes.length; at += 192) {
			int pid = ((bytes[at + 5] & 31) << 8) | (bytes[at + 6] & 255);
			if (pid == muxAudio.pid() && (bytes[at + 5] & 0x40) != 0 && (bytes[at + 7] & 0x10) != 0) {
				int payload = at + ((bytes[at + 7] & 0x20) == 0 ? 8 : 9 + (bytes[at + 8] & 255));
				assertThat(bytes[payload + 7] & 255).isEqualTo(0x81);
				sawExtendedAudio = true;
			}
		}
		assertThat(sawExtendedAudio).isTrue();
		Map<Integer, Long> shifts = new HashMap<>();
		for (var stream : info.getStreams())
			shifts.put(stream.getPid(), 1L);
		Path normalized = temp.resolve("normalized.m2ts");
		new PreservationTransportNormalizer(mapping, shifts, 1, info.getPmtPid(), info.getPcrPid(),
				Set.copyOf(shifts.keySet())).normalize(generated, normalized);
		Path finalEs = temp.resolve("final-es");
		new M2tsExtractor().extract(normalized, new M2tsParser().parse(normalized), finalEs, null, true);
		var result = M2tsTiming.read(finalEs.resolve(M2tsTimingManifest.FILE_NAME)).streams().stream()
				.filter(s -> s.codingType() == StreamCodingType.DOLBY_AC3).findFirst().orElseThrow();
		PreservationAc3Timeline.compare(muxEs.resolve(muxAudio.fileName()), muxAudio,
				finalEs.resolve(result.fileName()), result, -1);
	}
}
