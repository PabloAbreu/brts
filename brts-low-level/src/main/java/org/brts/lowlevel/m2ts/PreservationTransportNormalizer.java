/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservationTransportNormalizer.java' is part of BRTS.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;

/** Strict, bounded-memory, simultaneous PID and clock rewrite of a single-program M2TS. */
final class PreservationTransportNormalizer {

	private static final long PCR_WRAP = (1L << 33) * 300;
	private final Map<Integer, Integer> pids;
	private final Map<Integer, Long> shifts;
	private final long clockShift;
	private final int pmtPid;
	private final int pcrPid;
	private final Set<Integer> elementary;

	PreservationTransportNormalizer(Map<Integer, Integer> pids, Map<Integer, Long> shifts, long clockShift, int pmtPid,
			int pcrPid, Set<Integer> elementary) {
		this.pids = Map.copyOf(pids);
		this.shifts = Map.copyOf(shifts);
		this.clockShift = clockShift;
		this.pmtPid = pmtPid;
		this.pcrPid = pcrPid;
		this.elementary = Set.copyOf(elementary);
	}

	/** Mapping is ordinal AND codec-validated, injective, and includes every control PID. */
	static Map<Integer, Integer> mapping(M2tsInfo generated, M2tsInfo source,
			List<M2tsDescriptor.StreamEntry> requested) throws IOException {
		require(generated.getProgramPidMap() != null && generated.getProgramPidMap().size() == 1
				&& source.getProgramPidMap() != null && source.getProgramPidMap().size() == 1,
				"Only a single program is supported");
		require(generated.getStreams().size() == requested.size(), "Generated stream count changed");
		Map<Integer, Integer> result = new HashMap<>();
		result.put(0, 0);
		result.put(0x1fff, 0x1fff);
		put(result, generated.getPmtPid(), source.getPmtPid());
		if (generated.getSitPid() >= 0) {
			int from = generated.getSitPid();
			int to = source.getSitPid() >= 0 ? source.getSitPid() : from;
			require((from == 0x1f || from >= 0x20 && from < 0x1fff) && (to == 0x1f || to >= 0x20 && to < 0x1fff),
					"Invalid SIT PID");
			require(!result.containsKey(from), "SIT collides with another control PID");
			result.put(from, to);
		}
		for (int i = 0; i < requested.size(); i++) {
			var actual = generated.getStreams().get(i);
			var target = requested.get(i);
			require(actual.getStreamTypeByte() == target.getStreamTypeByte(),
					"Generated stream order/codec does not match descriptor at ordinal " + i);
			require(target.getPid() >= 0x20 && target.getPid() < 0x1fff, "Reserved target elementary PID");
			put(result, actual.getPid(), target.getPid());
		}
		// A dedicated PCR may move, but a PCR carried by an ES must remain on that same ES.
		if (result.containsKey(generated.getPcrPid())) {
			require(result.get(generated.getPcrPid()) == source.getPcrPid(), "PCR/elementary PID contract changed");
		} else {
			put(result, generated.getPcrPid(), source.getPcrPid());
		}
		require(new HashSet<>(result.values()).size() == result.size(), "PID collision with transport/control PID");
		return result;
	}

	private static void put(Map<Integer, Integer> map, int from, int to) throws IOException {
		require(from >= 0x20 && from < 0x1fff && to >= 0x20 && to < 0x1fff, "Invalid control PID");
		Integer previous = map.putIfAbsent(from, to);
		require(previous == null || previous == to, "Conflicting PID mapping");
	}

	void normalize(Path input, Path output) throws IOException {
		require(!Files.exists(output), "Normalized stage already exists: " + output);
		require(Files.size(input) > 0 && Files.size(input) % 192 == 0, "Truncated M2TS stage");
		Map<Integer, Section> sections = new HashMap<>();
		Map<Integer, Pes> pes = new HashMap<>();
		Map<Integer, Integer> continuity = new HashMap<>();
		long previousAts = -1;
		long previousPcr = -1;
		long currentPcr = Long.MIN_VALUE;
		try (RandomAccessFile in = new RandomAccessFile(input.toFile(), "r");
				RandomAccessFile out = new RandomAccessFile(output.toFile(), "rw")) {
			byte[] packet = new byte[192];
			for (long index = 0; index < in.length() / 192; index++) {
				in.readFully(packet);
				require(u(packet[4]) == 0x47 && (packet[5] & 0x80) == 0 && (packet[7] & 0xc0) == 0,
						"Invalid, errored or scrambled TS packet " + index);
				int pid = ((packet[5] & 0x1f) << 8) | u(packet[6]);
				require(pids.containsKey(pid), "Unmapped packet PID " + pid);
				long ats = read32(packet, 0) & 0x3fffffffL;
				if (previousAts >= 0) {
					long delta = Math.floorMod(ats - previousAts, 1L << 30);
					require(delta < (1L << 29), "Nonmonotonic/ambiguous ATS");
				}
				previousAts = ats;
				write32(packet, 0, (read32(packet, 0) & 0xc0000000L)
						| Math.floorMod(ats + Math.multiplyExact(clockShift, 300), 1L << 30));
				int control = (packet[7] >>> 4) & 3;
				require(control != 0, "Reserved adaptation control");
				int payload = 8;
				if ((control & 2) != 0) {
					int length = u(packet[8]);
					require(length <= 183, "Invalid adaptation length");
					payload = 9 + length;
					if (length > 0) {
						int flags = u(packet[9]);
						require((flags & 0x8f) == 0, "Unsupported discontinuity, OPCR, splice or adaptation extension");
						if ((flags & 0x10) != 0) {
							require(length >= 7 && pid == pcrPid, "Invalid/unexpected PCR");
							long pcr = readPcr(packet, 10);
							if (previousPcr >= 0) {
								long delta = Math.floorMod(pcr - previousPcr, PCR_WRAP);
								require(delta <= 270_000_000L, "PCR discontinuity or ambiguous wrap");
							}
							previousPcr = pcr;
							long rewritten = Math.floorMod(pcr + Math.multiplyExact(clockShift, 300), PCR_WRAP);
							writePcr(packet, 10, rewritten);
							currentPcr = rewritten / 300;
						}
					}
				}
				int mapped = pids.get(pid);
				packet[5] = (byte) ((packet[5] & 0xe0) | (mapped >>> 8));
				packet[6] = (byte) mapped;
				out.seek(index * 192);
				out.write(packet);
				if ((control & 1) == 0)
					continue;
				require(payload < 192, "Empty payload packet");
				if (pid == 0x1fff)
					continue;
				int cc = packet[7] & 15;
				Integer last = continuity.put(pid, cc);
				require(last == null || cc == ((last + 1) & 15), "Continuity error on PID " + pid);
				boolean start = (packet[5] & 0x40) != 0;
				if (pid == 0 || pid == pmtPid) {
					Section section = sections.computeIfAbsent(pid, k -> new Section(k));
					section.packet(packet, payload, start, index * 192, out);
				} else if (elementary.contains(pid)) {
					if (start) {
						Pes old = pes.get(pid);
						require(old == null || old.done, "Truncated split PES header");
						pes.put(pid, new Pes(shifts.get(pid), currentPcr));
					}
					Pes header = pes.get(pid);
					require(header != null, "Elementary stream begins without PES start");
					header.append(packet, payload, index * 192, out);
					header.protectClock(currentPcr);
					header.protectArrival(currentPcr, read32(packet, 0) & 0x3fffffffL);
				}
			}
			for (Section section : sections.values())
				require(section.bytes.size() == 0, "Truncated PSI section");
			for (Pes header : pes.values())
				require(header.done, "Truncated PES header at EOF");
			require(previousPcr >= 0, "No PCR available to protect decode clocks");
		}
	}

	private final class Section {
		final int pid;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final List<Long> positions = new ArrayList<>();
		int expected = -1;

		Section(int pid) {
			this.pid = pid;
		}

		void packet(byte[] packet, int offset, boolean start, long base, RandomAccessFile out) throws IOException {
			if (start) {
				int pointer = u(packet[offset++]);
				require(pointer <= 192 - offset, "Invalid PSI pointer");
				int end = offset + pointer;
				if (bytes.size() > 0) {
					while (offset < end)
						accept(packet[offset], base + offset++, out);
					require(bytes.size() == 0, "PSI pointer did not finish previous section");
				} else
					offset = end;
			} else
				require(bytes.size() > 0, "PSI continuation without section");
			while (offset < 192) {
				if (bytes.size() == 0 && u(packet[offset]) == 255)
					return;
				accept(packet[offset], base + offset, out);
				offset++;
			}
		}

		void accept(byte value, long position, RandomAccessFile out) throws IOException {
			bytes.write(value);
			positions.add(position);
			if (bytes.size() == 3) {
				byte[] b = bytes.toByteArray();
				expected = 3 + ((b[1] & 15) << 8) + u(b[2]);
				require(expected >= 12 && expected <= 4096 && (b[1] & 0x80) != 0, "Invalid PSI length/syntax");
			}
			if (bytes.size() == expected) {
				byte[] b = bytes.toByteArray();
				require(crc(b, b.length) == 0, "Invalid incoming PSI CRC");
				rewriteSection(b, pid);
				int checksum = crc(b, b.length - 4);
				write32(b, b.length - 4, checksum & 0xffffffffL);
				patch(out, positions, b);
				bytes.reset();
				positions.clear();
				expected = -1;
			}
		}
	}

	private void rewriteSection(byte[] b, int pid) throws IOException {
		require((b[5] & 1) == 1 && b[6] == 0 && b[7] == 0, "Non-current or multisection PSI unsupported");
		int end = b.length - 4;
		if (pid == 0) {
			require(b[0] == 0 && (end - 8) % 4 == 0, "Invalid PAT");
			for (int i = 8; i < end; i += 4)
				rewritePid(b, i + 2);
		} else {
			require(b[0] == 2 && end >= 12, "Expected PMT");
			rewritePid(b, 8);
			int info = ((b[10] & 15) << 8) | u(b[11]);
			descriptors(b, 12, 12 + info, end);
			int offset = 12 + info;
			Set<Integer> found = new HashSet<>();
			while (offset < end) {
				require(offset + 5 <= end, "Truncated PMT entry");
				int oldPid = ((b[offset + 1] & 31) << 8) | u(b[offset + 2]);
				require(elementary.contains(oldPid) && found.add(oldPid), "Unexpected/duplicate PMT elementary PID");
				rewritePid(b, offset + 1);
				int length = ((b[offset + 3] & 15) << 8) | u(b[offset + 4]);
				descriptors(b, offset + 5, offset + 5 + length, end);
				offset += 5 + length;
			}
			require(found.equals(elementary), "PMT stream set changed during mux");
		}
	}

	private void descriptors(byte[] b, int offset, int end, int limit) throws IOException {
		require(end <= limit, "Truncated descriptor loop");
		while (offset < end) {
			require(offset + 2 <= end, "Truncated descriptor");
			int tag = u(b[offset]);
			int length = u(b[offset + 1]);
			require(offset + 2 + length <= end, "Invalid descriptor length");
			if (tag == 0x09) {
				require(length >= 4, "Invalid CA descriptor");
				rewritePid(b, offset + 4);
			} else {
				// Only known PID-free MPEG/HDMV descriptors are safe to copy verbatim.
				require(Set.of(0x02, 0x03, 0x05, 0x0a, 0x0e, 0x0f, 0x10, 0x11, 0x1b, 0x1c, 0x28, 0x2a, 0x38, 0x81, 0x86,
						0x88).contains(tag), "Unsupported potentially PID-bearing descriptor tag " + tag);
			}
			offset += 2 + length;
		}
	}

	private void rewritePid(byte[] b, int offset) throws IOException {
		int old = ((b[offset] & 31) << 8) | u(b[offset + 1]);
		Integer target = pids.get(old);
		require(target != null, "Unmapped PSI PID reference " + old);
		b[offset] = (byte) ((b[offset] & 0xe0) | (target >>> 8));
		b[offset + 1] = (byte) (int) target;
	}

	private static final class Pes {
		final long shift;
		final long pcr;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final List<Long> positions = new ArrayList<>();
		int expected = 9;
		boolean done;
		Long decodeTimestamp;

		Pes(long shift, long pcr) {
			this.shift = shift;
			this.pcr = pcr;
		}

		void append(byte[] packet, int offset, long base, RandomAccessFile out) throws IOException {
			while (!done && offset < 192) {
				bytes.write(packet[offset]);
				positions.add(base + offset++);
				if (bytes.size() != expected)
					continue;
				byte[] b = bytes.toByteArray();
				if (expected == 9) {
					require(b[0] == 0 && b[1] == 0 && b[2] == 1 && (b[6] & 0xc0) == 0x80, "Unsupported PES syntax");
					// ESCR, ES rate, trick mode, additional copy info and previous-PES CRC
					// are not rewritten. The ordinary HDMV extension is PID/clock-free.
					require((b[6] & 0x30) == 0 && (b[7] & 0x3e) == 0, "Unsupported PES clock/extension fields");
					expected = 9 + u(b[8]);
					if (expected > 9)
						continue;
				}
				int flags = (b[7] >>> 6) & 3;
				require(flags != 1, "Reserved PTS/DTS flags");
				validateExtension(b, flags);
				if (flags >= 2) {
					require(b.length >= (flags == 3 ? 19 : 14), "Truncated PES timestamp");
					shiftTimestamp(b, 9, flags == 3 ? 3 : 2);
					if (flags == 3)
						shiftTimestamp(b, 14, 1);
					decodeTimestamp = readTimestamp(b, flags == 3 ? 14 : 9);
					protectClock(pcr);
				}
				patch(out, positions, b);
				done = true;
			}
		}

		private static void validateExtension(byte[] b, int timestampFlags) throws IOException {
			int offset = timestampFlags == 3 ? 19 : timestampFlags == 2 ? 14 : 9;
			require(offset <= b.length, "Truncated PES timestamp");
			int size = (u(b[4]) << 8) | u(b[5]);
			require(size == 0 || size >= b.length - 6, "PES length smaller than optional header");
			if ((b[7] & 1) != 0) {
				require(offset < b.length, "Truncated PES extension");
				int extension = u(b[offset++]);
				require((extension & 0x40) == 0, "Unsupported PES pack-header clock fields");
				if ((extension & 0x80) != 0)
					offset += 16; // Opaque, clock-free private data.
				if ((extension & 0x20) != 0) {
					require(offset + 2 <= b.length && (b[offset] & b[offset + 1] & 0x80) != 0,
							"Invalid PES sequence-counter extension");
					offset += 2;
				}
				if ((extension & 0x10) != 0) {
					require(offset + 2 <= b.length && (b[offset] & 0xc0) == 0x40, "Invalid PES P-STD extension");
					offset += 2;
				}
				if ((extension & 1) != 0) {
					require(offset < b.length && (b[offset] & 0x80) != 0, "Invalid PES extension-2 length");
					int length = b[offset++] & 0x7f;
					require(length > 0 && offset + length <= b.length, "Truncated PES extension-2");
					// stream_id_extension_flag=0 is HDMV's extended stream ID (e.g. 0x71).
					// flag=1 may carry TREF: do not silently preserve an unshifted clock.
					require((b[offset] & 0x80) == 0 || (b[offset] & 1) != 0, "Unsupported PES TREF clock field");
					offset += length;
				}
			}
			require(offset <= b.length, "Truncated PES extension");
			while (offset < b.length)
				require(u(b[offset++]) == 0xff, "Invalid PES header stuffing");
		}

		void shiftTimestamp(byte[] b, int offset, int prefix) throws IOException {
			require((u(b[offset]) >>> 4) == prefix && (b[offset] & b[offset + 2] & b[offset + 4] & 1) == 1,
					"Invalid PES timestamp markers");
			writeTimestamp(b, offset, Math.floorMod(readTimestamp(b, offset) + shift, 1L << 33));
		}

		void protectClock(long currentPcr) throws IOException {
			if (decodeTimestamp == null)
				return;
			require(currentPcr != Long.MIN_VALUE, "PES precedes first PCR; cannot validate buffering");
			long lead = M2tsTiming.unwrap33(decodeTimestamp, currentPcr) - currentPcr;
			require(lead >= 0 && lead <= 900_000, "Unsafe shifted decode/presentation clock relative to PCR");
		}

		void protectArrival(long currentPcr, long ats) throws IOException {
			if (decodeTimestamp == null)
				return;
			long pcr27 = currentPcr * 300;
			long delta = Math.floorMod(ats - pcr27 + (1L << 29), 1L << 30) - (1L << 29);
			require(Math.abs(delta) <= 27_000_000L, "ATS/PCR clock domains cannot be validated");
			long decode27 = M2tsTiming.unwrap33(decodeTimestamp, currentPcr) * 300;
			require(decode27 >= pcr27 + delta, "PES delivered after its decode/presentation deadline");
		}
	}

	static long readTimestamp(byte[] b, int o) {
		return ((long) (b[o] & 14) << 29) | ((long) u(b[o + 1]) << 22) | ((long) (b[o + 2] & 254) << 14)
				| ((long) u(b[o + 3]) << 7) | ((b[o + 4] & 254) >>> 1);
	}

	static void writeTimestamp(byte[] b, int o, long value) {
		b[o] = (byte) ((b[o] & 0xf0) | ((value >>> 29) & 14) | 1);
		b[o + 1] = (byte) (value >>> 22);
		b[o + 2] = (byte) (((value >>> 14) & 254) | 1);
		b[o + 3] = (byte) (value >>> 7);
		b[o + 4] = (byte) (((value << 1) & 254) | 1);
	}

	static int crc(byte[] bytes, int length) {
		int crc = -1;
		for (int i = 0; i < length; i++) {
			crc ^= u(bytes[i]) << 24;
			for (int bit = 0; bit < 8; bit++)
				crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
		}
		return crc;
	}

	private static long readPcr(byte[] b, int o) throws IOException {
		long base = ((long) u(b[o]) << 25) | ((long) u(b[o + 1]) << 17) | ((long) u(b[o + 2]) << 9)
				| ((long) u(b[o + 3]) << 1) | (u(b[o + 4]) >>> 7);
		int extension = ((b[o + 4] & 1) << 8) | u(b[o + 5]);
		require((b[o + 4] & 0x7e) == 0x7e && extension < 300, "Invalid PCR encoding");
		return base * 300 + extension;
	}

	private static void writePcr(byte[] b, int o, long value) {
		long base = value / 300;
		int extension = (int) (value % 300);
		b[o] = (byte) (base >>> 25);
		b[o + 1] = (byte) (base >>> 17);
		b[o + 2] = (byte) (base >>> 9);
		b[o + 3] = (byte) (base >>> 1);
		b[o + 4] = (byte) ((base << 7) | 0x7e | (extension >>> 8));
		b[o + 5] = (byte) extension;
	}

	private static void patch(RandomAccessFile out, List<Long> positions, byte[] b) throws IOException {
		for (int i = 0; i < b.length; i++) {
			out.seek(positions.get(i));
			out.write(b[i]);
		}
	}

	static int u(byte value) {
		return value & 255;
	}

	static long read32(byte[] b, int o) {
		return ((long) u(b[o]) << 24) | ((long) u(b[o + 1]) << 16) | ((long) u(b[o + 2]) << 8) | u(b[o + 3]);
	}

	static void write32(byte[] b, int o, long value) {
		for (int i = 3; i >= 0; i--) {
			b[o + i] = (byte) value;
			value >>>= 8;
		}
	}

	static void require(boolean condition, String message) throws IOException {
		if (!condition)
			throw new IOException("M2TS preservation: " + message);
	}
}
