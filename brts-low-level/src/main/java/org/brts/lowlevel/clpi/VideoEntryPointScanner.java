/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/VideoEntryPointScanner.java' is part of BRTS.
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

import java.util.function.LongUnaryOperator;

import org.brts.common.m2ts.PesTimestampParser;
import org.brts.common.model.StreamCodingType;

import lombok.RequiredArgsConstructor;

/**
 * Bounded, incremental video PES/Annex-B scanner. Only the optional PES header and the first three bytes of a
 * NAL/picture header are retained; parameter sets, SEI and picture payloads may be arbitrarily large.
 *
 * <p>
 * The caller supplies payloads for one video PID, in transport order. PES boundaries are not picture boundaries. A
 * timestamp belongs to the first picture beginning in its PES; additional pictures without timestamps are skipped,
 * rather than assigned an invented presentation time. Entry anchors include preceding AUD/parameter sets/SEI.
 */
@RequiredArgsConstructor
final class VideoEntryPointScanner {

	@FunctionalInterface
	interface EntryConsumer {
		void accept(long pts, long spn);
	}

	@RequiredArgsConstructor
	private static final class Pes {

		final long spn;

		long pts = -1;

		boolean used;

	}

	private final StreamCodingType codec;

	private final LongUnaryOperator timestamps;

	private final EntryConsumer entries;

	private final byte[] header = new byte[264];

	private int headerSize;

	private int headerEnd = 9;

	private int remaining = -1;

	private boolean accepting;

	private Pes pes;

	private int zeros;

	private Pes zeroPes;

	private Pes secondZeroPes;

	private Pes thirdZeroPes;

	private Pes nalPes;

	private Pes anchor;

	private final int[] nal = new int[3];

	private int nalSize = -1;

	private long malformedHeaders;

	private long untimestampedPictures;

	void accept(byte[] data, int offset, int length, boolean start, long spn) {
		if (start) {
			// An unfinished header or a short, explicitly bounded PES cannot be continued by a new PES.
			if (accepting && (headerSize < headerEnd || remaining > 0))
				reset();
			pes = new Pes(spn);
			headerSize = 0;
			headerEnd = 9;
			remaining = -1;
			accepting = true;
		}
		if (!accepting)
			return;
		for (int i = offset; i < offset + length && accepting; i++) {
			int b = data[i] & 0xFF;
			if (headerSize < headerEnd) {
				header[headerSize++] = (byte) b;
				if (headerSize == 9) {
					int flags = (header[7] >> 6) & 3;
					int optional = header[8] & 0xFF;
					int packetLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
					if (header[0] != 0 || header[1] != 0 || header[2] != 1 || (header[3] & 0xF0) != 0xE0
							|| (header[6] & 0xC0) != 0x80 || (header[6] & 0x30) != 0 || flags == 1
							|| optional < (flags == 3 ? 10 : flags == 2 ? 5 : 0)
							|| (packetLength != 0 && packetLength < 3 + optional)) {
						malformedHeaders++;
						reset();
						return;
					}
					headerEnd = 9 + optional;
					remaining = packetLength == 0 ? -1 : packetLength - 3 - optional;
				}
				if (headerSize == headerEnd && !completeHeader()) {
					malformedHeaders++;
					reset();
					return;
				}
			} else if (remaining != 0) {
				elementaryByte(b);
				if (remaining > 0)
					remaining--;
			}
		}
	}

	private boolean completeHeader() {
		int flags = (header[7] >> 6) & 3;
		if (flags >= 2) {
			if (!validTimestamp(9, flags) || (flags == 3 && !validTimestamp(14, 1)))
				return false;
			pes.pts = timestamps.applyAsLong(PesTimestampParser.decodeTimestamp(header, 9));
		}
		return true;
	}

	private boolean validTimestamp(int off, int prefix) {
		return ((header[off] & 0xF0) >> 4) == prefix && (header[off] & 1) != 0 && (header[off + 2] & 1) != 0
				&& (header[off + 4] & 1) != 0;
	}

	private void elementaryByte(int b) {
		if (b == 1 && zeros >= 2) {
			// A run may include trailing_zero_8bits from the previous PES/picture. Keep only the last
			// three zeros, and do not let an already consumed PES steal a fresh three-byte prefix's PTS.
			nalPes = zeroPes.used && zeros == 3 ? secondZeroPes : zeroPes;
			nalSize = 0;
			zeros = 0;
			return;
		}
		if (b == 0) {
			if (zeros == 0)
				zeroPes = pes;
			else if (zeros == 1)
				secondZeroPes = pes;
			else if (zeros == 2)
				thirdZeroPes = pes;
			else {
				zeroPes = secondZeroPes;
				secondZeroPes = thirdZeroPes;
				thirdZeroPes = pes;
			}
			zeros = Math.min(3, zeros + 1);
		} else {
			zeros = 0;
		}
		if (nalSize < 0 || nalSize == nal.length)
			return;
		nal[nalSize++] = b;
		if (codec == StreamCodingType.H265_HEVC && nalSize == 3) {
			int type = (nal[0] & 0x7E) >> 1;
			int layer = ((nal[0] & 1) << 5) | (nal[1] >> 3);
			if ((nal[0] & 0x80) != 0 || (nal[1] & 7) == 0) {
				anchor = null;
				return;
			}
			if (layer != 0)
				return;
			if (type >= 32) {
				if (type == 35)
					anchor = nalPes;
				else if ((type >= 32 && type <= 34) || type == 39)
					prefix();
			} else if ((nal[2] & 0x80) != 0) {
				picture(type >= 16 && type <= 21);
			}
		} else if (codec == StreamCodingType.H264_AVC && nalSize == 2) {
			int type = nal[0] & 0x1F;
			if ((nal[0] & 0x80) != 0) {
				anchor = null;
				return;
			}
			if (type == 9)
				anchor = nalPes;
			else if (type == 6 || type == 7 || type == 8)
				prefix();
			else if ((type == 1 || type == 2 || type == 5) && (nal[1] & 0x80) != 0)
				// first_mb_in_slice == 0 is the one-bit Exp-Golomb code '1'.
				picture(type == 5);
		} else if (codec == StreamCodingType.MPEG2_VIDEO && nalSize == 3) {
			if (nal[0] == 0)
				picture(((nal[2] >> 3) & 7) == 1);
			else if (nal[0] == 0xB3 || nal[0] == 0xB8)
				prefix();
		}
	}

	private void prefix() {
		if (anchor == null)
			anchor = nalPes;
	}

	private void picture(boolean keyframe) {
		Pes beginning = anchor != null ? anchor : nalPes;
		Pes timestamp = beginning.pts >= 0 && !beginning.used ? beginning : nalPes;
		if (!timestamp.used && timestamp.pts >= 0 && keyframe)
			entries.accept(timestamp.pts, beginning.spn);
		else if (keyframe)
			untimestampedPictures++;
		// Consume even non-keyframe PTS: it must not leak into a later picture in the same PES.
		timestamp.used = true;
		beginning.used = true;
		anchor = null;
	}

	long malformedHeaders() {
		return malformedHeaders;
	}

	long untimestampedPictures() {
		return untimestampedPictures;
	}

	/** Discard partial PES/NAL state after transport loss or at end of input; never synthesize an EOF entry. */
	void reset() {
		accepting = false;
		pes = null;
		anchor = null;
		nalPes = null;
		zeroPes = null;
		secondZeroPes = null;
		thirdZeroPes = null;
		zeros = 0;
		nalSize = -1;
	}

}
