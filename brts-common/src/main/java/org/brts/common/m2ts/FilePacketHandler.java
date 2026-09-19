package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/FilePacketHandler.java' is part of BRTS.
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.utils.Extensions;
import org.brts.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link M2tsPacketHandler} that writes each PID to a separate file on disk.
 * <p>
 * PES headers are stripped: when a packet carries the Payload Unit Start Indicator and begins with the {@code 00 00 01}
 * PES start-code, the PES header is skipped and only the elementary stream payload is written.
 * <p>
 * Output files are named {@code <outputDir>/pid_<hex>.&lt;ext&gt;} where the extension is derived from the stream's
 * coding type.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * M2tsInfo info = new M2tsParser().parse(inputPath);
 * Set<Integer> pidFilter = Set.of(4113, 4352);
 * try (FilePacketHandler handler = new FilePacketHandler(info, outputDir, pidFilter)) {
 * 	new M2tsDemuxer().demux(inputPath, info, handler, pidFilter);
 * }
 * }</pre>
 */
@Slf4j
public class FilePacketHandler implements M2tsPacketHandler {

	private final Map<Integer, OutputStream> pidToStream = new LinkedHashMap<>();

	private final Map<Integer, String> pidToFile = new LinkedHashMap<>();

	/**
	 * Creates a file-per-PID handler.
	 *
	 * @param info      stream metadata (for extension lookup)
	 * @param outputDir directory where elementary stream files are written
	 * @param pidFilter only these PIDs are opened; {@code null} or empty = all
	 * @throws IOException on I/O error creating output files
	 */
	public FilePacketHandler(M2tsInfo info, Path outputDir, Set<Integer> pidFilter) throws IOException {
		Files.createDirectories(outputDir);

		List<M2tsStreamInfo> streams = info.getStreams() != null ? info.getStreams() : List.of();
		for (M2tsStreamInfo s : streams) {
			if (pidFilter != null && !pidFilter.isEmpty() && !pidFilter.contains(s.getPid()))
				continue;
			String ext = Extensions.extensionForStream(s);
			Path outFile = outputDir.resolve(String.format("pid_%04x.%s", s.getPid(), ext));
			pidToStream.put(s.getPid(), new BufferedOutputStream(Files.newOutputStream(outFile)));
			pidToFile.put(s.getPid(), outFile.toString());
			log.debug("  PID 0x{} → {}", Integer.toHexString(s.getPid()), outFile.getFileName());
		}
	}

	@Override
	public void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart, long packetIndex,
			long ats) throws IOException {

		OutputStream out = pidToStream.get(pid);
		if (out == null)
			return;

		int payloadOff = offset;
		int payloadLen = length;

		// Strip PES header only on packets with Payload Unit Start Indicator.
		// The 00 00 01 pattern also appears as MPEG-2 start codes inside
		// elementary stream data (slice headers, etc.) and must NOT be stripped there.
		if (payloadUnitStart && payloadLen > 8 && payload[payloadOff] == 0x00 && payload[payloadOff + 1] == 0x00
				&& payload[payloadOff + 2] == 0x01) {
			int pesHeaderLen = (payload[payloadOff + 8] & 0xFF) + 9;
			payloadOff += pesHeaderLen;
			payloadLen = length - (payloadOff - offset);
			if (pid == 0x1400) {
				log.debug(" PES header of {} bytes stripped, payload now {} bytes", pesHeaderLen, payloadLen);
				// then print bytes of payload for debugging
				log.debug("  Payload start: {}", StringUtils.bytesToHex(payload, payloadOff, payloadLen));
			}
			if (payloadLen <= 0)
				return;
		}

		out.write(payload, payloadOff, payloadLen);
	}

	@Override
	public void close() throws IOException {
		IOException firstError = null;
		for (OutputStream os : pidToStream.values()) {
			try {
				os.close();
			} catch (IOException e) {
				if (firstError == null)
					firstError = e;
			}
		}
		for (Map.Entry<Integer, String> e : pidToFile.entrySet()) {
			log.info("  Extracted PID 0x{} → {}", Integer.toHexString(e.getKey()), e.getValue());
		}
		if (firstError != null)
			throw firstError;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/** Returns the set of output file paths created by this handler. */
	public Map<Integer, String> getOutputFiles() {
		return Collections.unmodifiableMap(pidToFile);
	}
}
