/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservationAc3Timeline.java' is part of BRTS.
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

import static org.brts.lowlevel.m2ts.PreservationTransportNormalizer.require;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;

/** AC-3 syncframe/sample clock proof, independent of PES grouping (including PES splits inside a frame). */
final class PreservationAc3Timeline {

	private static final int[] SAMPLE_RATES = { 48_000, 44_100, 32_000 };
	private static final int[] BITRATES = { 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448,
			512, 576, 640 };
	private static final int[] WORDS_44100 = { 69, 87, 104, 121, 139, 174, 208, 243, 278, 348, 417, 487, 557, 696, 835,
			975, 1114, 1253, 1393 };

	private PreservationAc3Timeline() {
	}

	static void validate(Path es, StreamTiming timing) throws IOException {
		try (Cursor cursor = new Cursor(es, timing)) {
			while (cursor.next() != null) {
				// Inspect every syncframe and every PES anchor, not just the first/last PTS.
			}
		}
	}

	static void compare(Path sourceEs, StreamTiming source, Path generatedEs, StreamTiming generated, long shift)
			throws IOException {
		require(Files.mismatch(sourceEs, generatedEs) == -1, "Copied AC3 elementary bytes changed");
		try (Cursor original = new Cursor(sourceEs, source); Cursor muxed = new Cursor(generatedEs, generated)) {
			for (;;) {
				Frame a = original.next();
				Frame b = muxed.next();
				require((a == null) == (b == null), "AC3 frame count changed");
				if (a == null)
					break;
				require(a.offset == b.offset && a.size == b.size && a.sampleRate == b.sampleRate
						&& a.samples == b.samples && a.pts == Math.addExact(b.pts, shift),
						"AC3 elementary-frame sample grid changed for PID " + source.pid());
			}
		}
	}

	private record Frame(long offset, int size, int sampleRate, int samples, long pts) {
	}

	private static final class Cursor implements AutoCloseable {
		private final RandomAccessFile in;
		private final StreamTiming timing;
		private long samples;
		private int sampleRate;
		private int anchor;

		Cursor(Path es, StreamTiming timing) throws IOException {
			this.timing = timing;
			PreservingM2tsClipWriter.validateTiming(timing);
			require(timing.codingType() == StreamCodingType.DOLBY_AC3, "AC3 timeline requires AC3, not EAC3/DTS");
			require(timing.pesTimings().getFirst().outputOffset() == 0
					&& timing.firstPts90k().equals(timing.pesTimings().getFirst().pts90k()),
					"AC3 lacks an initial elementary-frame anchor");
			long previous = -1;
			long size = Files.size(es);
			for (var pes : timing.pesTimings()) {
				require(pes.outputOffset() > previous && pes.outputOffset() < size, "Invalid AC3 PES byte offset");
				require(pes.dts90k() == null || pes.dts90k().equals(pes.pts90k()),
						"Unsupported distinct AC3 decode clock");
				previous = pes.outputOffset();
			}
			in = new RandomAccessFile(es.toFile(), "r");
		}

		Frame next() throws IOException {
			long offset = in.getFilePointer();
			if (offset == in.length()) {
				require(samples > 0 && anchor == timing.pesTimings().size(), "AC3 PES has no commencing syncframe");
				return null;
			}
			require(in.length() - offset >= 7, "Truncated AC3 syncframe header");
			require(in.readUnsignedShort() == 0x0b77, "Invalid AC3 syncword at ES offset " + offset);
			in.skipBytes(2); // CRC1, preserved by byte comparison, not rewritten.
			int codes = in.readUnsignedByte();
			int bsid = in.readUnsignedByte() >>> 3;
			int fscod = codes >>> 6;
			int sizeCode = codes & 63;
			require(fscod < 3 && sizeCode < 38 && bsid <= 10, "Unsupported AC3 frame header (including EAC3)");
			int rate = SAMPLE_RATES[fscod] >> Math.max(0, bsid - 8);
			int bitrate = BITRATES[sizeCode >>> 1];
			int size = switch (fscod) {
			case 0 -> bitrate * 4;
			case 1 -> (WORDS_44100[sizeCode >>> 1] + (sizeCode & 1)) * 2;
			default -> bitrate * 6;
			};
			require(size <= in.length() - offset, "Truncated AC3 syncframe at ES offset " + offset);
			if (sampleRate == 0)
				sampleRate = rate;
			require(rate == sampleRate, "Changing AC3 sample rate is unsupported");
			// One rational sample clock, never repeated rounded frame durations. Fractional-tick
			// sources must agree with this initial-anchor/floor convention or fail closed.
			long pts = Math.addExact(timing.firstPts90k(), Math.multiplyExact(samples, 90_000) / rate);
			while (anchor < timing.pesTimings().size() && timing.pesTimings().get(anchor).outputOffset() <= offset) {
				var pes = timing.pesTimings().get(anchor++);
				require(anchor == timing.pesTimings().size() || timing.pesTimings().get(anchor).outputOffset() > offset,
						"AC3 timestamped PES contains no commencing syncframe");
				require(pes.pts90k() == pts, "AC3 PES anchor disagrees with exact sample clock at ES offset " + offset);
			}
			in.seek(offset + size);
			samples = Math.addExact(samples, 1536);
			return new Frame(offset, size, rate, 1536, pts);
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
