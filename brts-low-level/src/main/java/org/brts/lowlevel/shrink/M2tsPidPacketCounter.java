/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsPidPacketCounter.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts source packets by PID without decoding payloads; useful for exact source-byte accounting.
 *
 * Performance note: this implementation has the obvious drawback of having to read the entire file sequentially and may
 * be slow for large files. The probable next processing step for most users being to re-read the whole file for actual
 * processing, this means that huge files may be read twice.
 */
public class M2tsPidPacketCounter {

	private static final int SOURCE_PACKET_SIZE = 192;
	private static final int SYNC_BYTE = 0x47;
	private static final int BUFFER_SIZE = SOURCE_PACKET_SIZE * 4096;

	public Map<Integer, Long> count(Path path) throws IOException {
		long size = Files.size(path);
		if (size == 0 || size % SOURCE_PACKET_SIZE != 0) {
			throw new IOException("M2TS must be nonempty and aligned to 192-byte source packets: " + path);
		}
		Map<Integer, Long> counts = new LinkedHashMap<>();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		long packetIndex = 0;
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
			while (channel.read(buffer) >= 0) {
				buffer.flip();
				while (buffer.remaining() >= SOURCE_PACKET_SIZE) {
					int packetStart = buffer.position();
					if ((buffer.get(packetStart + 4) & 0xFF) != SYNC_BYTE) {
						throw new IOException("Lost TS sync byte at source packet " + packetIndex + " in " + path);
					}
					int pid = ((buffer.get(packetStart + 5) & 0x1F) << 8) | (buffer.get(packetStart + 6) & 0xFF);
					counts.merge(pid, 1L, Long::sum);
					buffer.position(packetStart + SOURCE_PACKET_SIZE);
					packetIndex++;
				}
				buffer.compact();
			}
		}
		if (buffer.position() != 0 || packetIndex * SOURCE_PACKET_SIZE != size) {
			throw new IOException("Incomplete source packet while scanning " + path);
		}
		return Collections.unmodifiableMap(counts);
	}
}
