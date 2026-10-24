/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsShrinkInputResolverTest.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsShrinkInputResolverTest {

	@TempDir
	Path tempDir;

	@Test
	void resolvesPairAndBuildsJoinedPidInventory() throws Exception {
		Path streamDir = Files.createDirectories(tempDir.resolve("BDMV/STREAM"));
		Path clipDir = Files.createDirectories(tempDir.resolve("BDMV/CLIPINF"));
		Path m2ts = Files.write(streamDir.resolve("00001.m2ts"), packets(0x1011, 0x1100, 0x1100));
		Path clpi = Files.writeString(clipDir.resolve("00001.clpi"), "fixture");
		M2tsShrinkInputResolver resolver = new M2tsShrinkInputResolver(path -> m2tsInfo(), path -> clipInfo(),
				new M2tsPidPacketCounter());

		var inventory = resolver.resolve(tempDir, "00001");

		assertThat(inventory.m2tsPath()).isEqualTo(m2ts);
		assertThat(inventory.clpiPath()).isEqualTo(clpi);
		assertThat(inventory.sourceBytes()).isEqualTo(3L * 192);
		assertThat(inventory.duration90k()).isEqualTo(900_000);
		assertThat(inventory.track(0x1100).sourcePacketCount()).isEqualTo(2);
	}

	@Test
	void rejectsInvalidIdentifierMissingMateAndMetadataMismatch() throws Exception {
		var resolver = new M2tsShrinkInputResolver(path -> m2tsInfo(), path -> clipInfo(), new M2tsPidPacketCounter());
		assertThatThrownBy(() -> resolver.resolve(tempDir, "1001")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("five digits");

		Path streamDir = Files.createDirectories(tempDir.resolve("BDMV/STREAM"));
		Path clipDir = Files.createDirectories(tempDir.resolve("BDMV/CLIPINF"));
		Files.write(streamDir.resolve("00001.m2ts"), packets(0x1011, 0x1100));
		Files.writeString(clipDir.resolve("00001.clpi"), "fixture");
		var mismatch = new M2tsShrinkInputResolver(path -> m2tsInfo(), path -> {
			ClipInfo clip = clipInfo();
			clip.getStreams().getFirst().setCodingType(StreamCodingType.DTS);
			return clip;
		}, new M2tsPidPacketCounter());
		assertThatThrownBy(() -> mismatch.resolve(tempDir, "00001")).isInstanceOf(java.io.IOException.class)
				.hasMessageContaining("coding type mismatch");
	}

	private static M2tsInfo m2tsInfo() {
		M2tsInfo info = new M2tsInfo();
		M2tsStreamInfo video = new M2tsStreamInfo();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		M2tsStreamInfo audio = new M2tsStreamInfo();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		info.setStreams(List.of(video, audio));
		return info;
	}

	private static ClipInfo clipInfo() {
		ClipInfo info = new ClipInfo();
		info.setDuration(Timestamp.ofTicks(900_000));
		ClipStream video = new ClipStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		ClipStream audio = new ClipStream();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		info.setStreams(List.of(video, audio));
		return info;
	}

	private static byte[] packets(int... pids) {
		byte[] bytes = new byte[pids.length * 192];
		for (int i = 0; i < pids.length; i++) {
			int offset = i * 192;
			bytes[offset + 4] = 0x47;
			bytes[offset + 5] = (byte) ((pids[i] >> 8) & 0x1F);
			bytes[offset + 6] = (byte) pids[i];
		}
		return bytes;
	}
}
