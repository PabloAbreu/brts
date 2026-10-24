/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/M2tsClpiRegenBuilderEpScanTest.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.brts.lowlevel.clpi.M2tsEpFixtures.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.EpMap.EpMapEntry;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class M2tsClpiRegenBuilderEpScanTest {

	@TempDir
	Path directory;

	private ClipInfo build(byte[] input) throws Exception {
		Path path = directory.resolve("00001.m2ts");
		Files.write(path, input);
		ClpiRegenOptions options = new ClpiRegenOptions();
		options.setProbeMode(ClpiRegenOptions.ProbeMode.DISABLED);
		// Synthetic EP fixtures omit video metadata; supply it explicitly instead of probing.
		ClpiRegenOptions.VideoOverrides video = new ClpiRegenOptions.VideoOverrides();
		video.setVideoFormat(6); // 1080p
		video.setFrameRate(1); // 23.976 fps
		video.setAspectRatio(3); // 16:9
		options.getVideoStreams().put(VIDEO_PID, video);
		return new M2tsClpiRegenBuilder((p, videos) -> {
			throw new AssertionError("Native probing must not be used in EP scan tests");
		}).build(path, "00001", options);
	}

	private List<EpMapEntry> entries(ClipInfo clip) {
		return clip.getEpMap() == null ? List.of() : clip.getEpMap().getStreams().getFirst().getEntries();
	}

	@Test
	void detectsUhdCraAndIdrAfterLargeMetadataAndRoundTripsTheirOriginalAnchors() throws Exception {
		byte[] metadata = new byte[2_000];
		Arrays.fill(metadata, (byte) 0x55);
		byte[] video = pes(92_160, join(hevc(35), hevc(32), hevc(33), hevc(34), hevc(39), metadata, hevc(21)));
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		input.writeBytes(tables(0x24));
		input.writeBytes(pcr(90_000 * 300L));
		long firstSpn = input.size() / 192;
		int counter = 0;
		for (int offset = 0; offset < video.length; offset += 151)
			input.writeBytes(packet(VIDEO_PID, offset == 0, counter++,
					Arrays.copyOfRange(video, offset, Math.min(video.length, offset + 151))));
		input.writeBytes(pcr(180_000 * 300L));
		long secondSpn = input.size() / 192;
		input.writeBytes(packet(VIDEO_PID, true, counter, pes(184_320, hevc(19))));

		ClipInfo clip = build(input.toByteArray());
		assertThat(clip.getVersion()).isEqualTo("0300");
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(92_160L, 184_320L);
		assertThat(entries(clip)).extracting(EpMapEntry::getSpn).containsExactly(firstSpn, secondSpn);
		assertThat(clip.getDuration().getTicks()).isEqualTo(92_160L);
		assertThat(clip.getNumSourcePackets()).isEqualTo(input.size() / 192);

		ByteArrayOutputStream binary = new ByteArrayOutputStream();
		new ClipInfoWriter().write(clip, binary);
		ClipInfo parsed = new ClipInfoParser().parse(new ByteArrayInputStream(binary.toByteArray()));
		assertThat(entries(parsed)).extracting(EpMapEntry::getPtsTicks).containsExactly(92_160L, 184_320L);
		assertThat(entries(parsed)).extracting(EpMapEntry::getSpn).containsExactly(firstSpn, secondSpn);
	}

	@Test
	void splitsThePesHeaderAcrossTransportPackets() throws Exception {
		byte[] video = pes(90_000, hevc(21));
		ClipInfo clip = build(join(tables(0x24), packet(VIDEO_PID, true, 0, Arrays.copyOfRange(video, 0, 5)),
				packet(VIDEO_PID, false, 1, Arrays.copyOfRange(video, 5, 11)),
				packet(VIDEO_PID, false, 2, Arrays.copyOfRange(video, 11, video.length))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(90_000L);
		assertThat(entries(clip)).extracting(EpMapEntry::getSpn).containsExactly(2L);
	}

	@Test
	void scansDedicatedPcrPidAndUsesItAsFallbackWithoutVideoPts() throws Exception {
		ClipInfo clip = build(join(tables(0x24), pcr(90_000 * 300L), packet(VIDEO_PID, true, 0, pes(-1, hevc(21))),
				pcr(180_000 * 300L)));
		assertThat(entries(clip)).isEmpty();
		assertThat(clip.getTsRecordingStartPts().getTicks()).isEqualTo(90_000);
		assertThat(clip.getTsRecordingEndPts().getTicks()).isEqualTo(180_000);
	}

	@Test
	void doesNotTreatDedicatedPcrPidPayloadAsVideo() throws Exception {
		ClipInfo clip = build(join(tables(0x24), packet(PCR_PID, true, 0, pes(1_000, hevc(19))),
				packet(VIDEO_PID, true, 0, pes(90_000, hevc(21)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(90_000L);
		assertThat(clip.getTsRecordingStartPts().getTicks()).isEqualTo(90_000);
	}

	@Test
	void ignoresRetransmissionsAndResynchronizesAfterMissingPackets() throws Exception {
		byte[] first = packet(VIDEO_PID, true, 0, pes(90_000, hevc(21)));
		byte[] retransmit = first.clone();
		retransmit[3] = 1; // ATS can change while the embedded TS packet is retransmitted unchanged.
		byte[] unfinished = packet(VIDEO_PID, true, 1, pes(180_000, bytes(0, 0)));
		ClipInfo clip = build(
				join(tables(0x24), first, retransmit, unfinished, packet(VIDEO_PID, false, 3, bytes(1, 38, 1, 0x80)), // lost
																														// counter
																														// 2
						packet(VIDEO_PID, true, 4, pes(270_000, hevc(19)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(90_000L, 270_000L);
		assertThat(entries(clip)).extracting(EpMapEntry::getSpn).containsExactly(2L, 6L);
	}

	@Test
	void transportErrorPacketCannotCompleteAPartialNal() throws Exception {
		byte[] damaged = packet(VIDEO_PID, false, 1, bytes(0x55));
		damaged[5] |= (byte) 0x80; // demux drops this TEI packet; counter 2 must expose the gap.
		ClipInfo clip = build(join(tables(0x24), packet(VIDEO_PID, true, 0, pes(90_000, bytes(0, 0))), damaged,
				packet(VIDEO_PID, false, 2, bytes(1, 38, 1, 0x80)),
				packet(VIDEO_PID, true, 3, pes(180_000, hevc(21)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(180_000L);
	}

	@Test
	void explicitTransportDiscontinuityDiscardsPartialVideoState() throws Exception {
		byte[] discontinuity = packet(VIDEO_PID, false, 1, bytes(1, 38, 1, 0x80));
		discontinuity[9] |= (byte) 0x80;
		ClipInfo clip = build(join(tables(0x24), packet(VIDEO_PID, true, 0, pes(90_000, bytes(0, 0))), discontinuity,
				packet(VIDEO_PID, true, 2, pes(180_000, hevc(21)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(180_000L);
	}

	@Test
	void dropsScrambledPayloadUntilTheNextValidPes() throws Exception {
		byte[] scrambled = packet(VIDEO_PID, true, 0, pes(90_000, hevc(21)));
		scrambled[7] |= (byte) 0x80;
		ClipInfo clip = build(join(tables(0x24), scrambled, packet(VIDEO_PID, false, 1, hevc(19)),
				packet(VIDEO_PID, true, 2, pes(180_000, hevc(21)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(180_000L);
	}

	@Test
	void unwrapsVideoPtsAndAllowsOrdinaryBPictureReordering() throws Exception {
		long modulo = M2tsClpiRegenBuilder.PTS_MODULO;
		ClipInfo clip = build(join(tables(0x24), packet(VIDEO_PID, true, 0, pes(modulo - 10_000, hevc(19))),
				packet(VIDEO_PID, true, 1, pes(modulo - 2_000, hevc(1))),
				packet(VIDEO_PID, true, 2, pes(modulo - 6_000, hevc(0))),
				packet(VIDEO_PID, true, 3, pes(2_000, hevc(21)))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(modulo - 10_000, modulo + 2_000);
		assertThat(clip.getDuration().getTicks()).isEqualTo(12_000);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0x1B, 0x02 })
	void retainsAvcAndMpeg2EntryPoints(int streamType) throws Exception {
		byte[] key = streamType == 0x1B ? join(avc(6), avc(5)) : mpeg(1);
		byte[] nonKey = streamType == 0x1B ? avc(1) : mpeg(2);
		ClipInfo clip = build(join(tables(streamType), packet(VIDEO_PID, true, 0, pes(90_000, key)),
				packet(VIDEO_PID, true, 1, pes(180_000, nonKey))));
		assertThat(entries(clip)).extracting(EpMapEntry::getPtsTicks).containsExactly(90_000L);
		assertThat(clip.getVersion()).isEqualTo("0200");
	}

}
