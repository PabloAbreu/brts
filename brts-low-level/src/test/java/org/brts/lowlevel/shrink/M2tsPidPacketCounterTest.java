/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsPidPacketCounterTest.java' is part of BRTS.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsPidPacketCounterTest {

	@TempDir
	Path tempDir;

	@Test
	void countsEachAlignedSourcePacketByPid() throws Exception {
		Path source = tempDir.resolve("source.m2ts");
		Files.write(source, packets(0x1011, 0x1100, 0x1011));

		var counts = new M2tsPidPacketCounter().count(source);

		assertThat(counts).containsEntry(0x1011, 2L).containsEntry(0x1100, 1L);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum() * 192).isEqualTo(Files.size(source));
		assertThatThrownBy(() -> counts.put(0x1200, 1L)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsMisalignedOrUnsynchronizedFiles() throws Exception {
		Path source = tempDir.resolve("bad.m2ts");
		Files.write(source, new byte[191]);
		assertThatThrownBy(() -> new M2tsPidPacketCounter().count(source)).isInstanceOf(java.io.IOException.class)
				.hasMessageContaining("aligned");

		byte[] packet = packets(0x1011);
		packet[4] = 0;
		Files.write(source, packet);
		assertThatThrownBy(() -> new M2tsPidPacketCounter().count(source)).isInstanceOf(java.io.IOException.class)
				.hasMessageContaining("sync byte");
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
