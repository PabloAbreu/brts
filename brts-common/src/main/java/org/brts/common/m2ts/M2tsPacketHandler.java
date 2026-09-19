package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/M2tsPacketHandler.java' is part of BRTS.
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

import java.io.IOException;

/**
 * Callback interface for receiving demultiplexed MPEG-2 TS payload data.
 * <p>
 * Implementations decide where the raw elementary stream bytes go — to files on disk, into an MKV container, or
 * anywhere else. The demuxer ({@link M2tsDemuxer}) invokes these methods once per relevant TS packet.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 * <li>{@link #onPayload} is called for every matching packet.</li>
 * <li>{@link #close()} is called once when demuxing is complete (or on error).</li>
 * </ol>
 */
public interface M2tsPacketHandler extends AutoCloseable {

	/**
	 * Called for every demuxed payload chunk belonging to a tracked PID.
	 *
	 * Note that for PCR packets, the payload buffer is the full TS packet and offset will be zero.
	 *
	 * @param pid              the PID of the elementary stream
	 * @param payload          buffer containing the raw ES bytes (not the full TS packet)
	 * @param offset           start offset within {@code payload}
	 * @param length           number of valid bytes starting at {@code offset}
	 * @param payloadUnitStart true if this packet carries the Payload Unit Start Indicator
	 * @param packetIndex      zero-based index of the source packet in the M2TS file
	 * @param ats              30-bit Arrival Time Stamp from the TP_extra_header (27 MHz ticks)
	 * @throws IOException on I/O error
	 */
	void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart, long packetIndex,
			long ats) throws IOException;

	/**
	 * Release any resources held by this handler. Called once when demuxing completes (successfully or after an error).
	 */
	@Override
	void close() throws IOException;

}
