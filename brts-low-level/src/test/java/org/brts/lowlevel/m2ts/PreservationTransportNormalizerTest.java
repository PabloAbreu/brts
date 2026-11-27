/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservationTransportNormalizerTest.java' is part of BRTS.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservationTransportNormalizerTest {
	@TempDir
	Path temp;
	private static final long WRAP = 1L << 33;

	@Test
	void sparseAudioPidSplitPsiPesAndWrappedClocks() throws Exception {
		List<byte[]> packets = fixture(0x05);
		Path input = write(packets, "input.m2ts");
		Path output = temp.resolve("output.m2ts");
		normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_001L), Map.of(0x1011, 0x1011, 0x1100, 0x1102)).normalize(input,
				output);
		List<byte[]> result = packets(output);
		assertThat(result).hasSize(packets.size());
		assertThat(result.stream().map(PreservationTransportNormalizerTest::pid).toList()).contains(0x1102)
				.doesNotContain(0x1100);
		byte[] pat = section(result, 0);
		byte[] pmt = section(result, 0x100);
		assertThat(PreservationTransportNormalizer.crc(pat, pat.length)).isZero();
		assertThat(PreservationTransportNormalizer.crc(pmt, pmt.length)).isZero();
		assertThat(((pmt[8] & 31) << 8) | (pmt[9] & 255)).isEqualTo(0x1001);
		int audioEntry = 12 + 240 + 5;
		assertThat(((pmt[audioEntry + 1] & 31) << 8) | (pmt[audioEntry + 2] & 255)).isEqualTo(0x1102);
		byte[] video = payloads(result, 0x1011);
		assertThat(PreservationTransportNormalizer.readTimestamp(video, 9)).isEqualTo(5_000);
		assertThat(PreservationTransportNormalizer.readTimestamp(video, 14)).isEqualTo(2_000);
		byte[] audio = payloads(result, 0x1102);
		assertThat(PreservationTransportNormalizer.readTimestamp(audio, 9)).isEqualTo(4_001);
		assertThat(Arrays.copyOfRange(audio, 14, audio.length)).containsExactly(1, 2, 3, 4, 5, 6);
		long originalAts = PreservationTransportNormalizer.read32(packets.getFirst(), 0) & 0x3fffffffL;
		long rewrittenAts = PreservationTransportNormalizer.read32(result.getFirst(), 0) & 0x3fffffffL;
		assertThat(rewrittenAts).isEqualTo(Math.floorMod(originalAts + 3_000_000L, 1L << 30));
		byte[] pcr = result.getFirst();
		long pcrBase = ((long) (pcr[10] & 255) << 25) | ((long) (pcr[11] & 255) << 17) | ((long) (pcr[12] & 255) << 9)
				| ((long) (pcr[13] & 255) << 1) | ((pcr[14] & 255) >>> 7);
		assertThat(pcrBase).isEqualTo(WRAP - 10_000);
	}

	@Test
	void swapsAreSimultaneousRatherThanSequential() throws Exception {
		Path output = temp.resolve("swap.m2ts");
		normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_000L), Map.of(0x1011, 0x1100, 0x1100, 0x1011))
				.normalize(write(fixture(0x05), "input.m2ts"), output);
		assertThat(PreservationTransportNormalizer.readTimestamp(payloads(packets(output), 0x1100), 9))
				.isEqualTo(5_000);
		assertThat(PreservationTransportNormalizer.readTimestamp(payloads(packets(output), 0x1011), 9))
				.isEqualTo(4_000);
	}

	@Test
	void rejectsControlCollisionAndOrdinalCodecMismatch() {
		M2tsInfo generated = info(0x1011, 0x1100);
		M2tsInfo source = info(0x1011, 0x1102);
		var video = entry(0x1011, 0x1b);
		var audio = entry(0x100, 0x81);
		assertThatThrownBy(() -> PreservationTransportNormalizer.mapping(generated, source, List.of(video, audio)))
				.isInstanceOf(IOException.class).hasMessageContaining("collision");
		assertThatThrownBy(() -> PreservationTransportNormalizer.mapping(generated, source,
				List.of(entry(0x1102, 0x81), entry(0x1011, 0x1b)))).isInstanceOf(IOException.class)
				.hasMessageContaining("ordinal");
	}

	@Test
	void sparseMappingUsesRequestedPidNotContiguousAllocation() throws Exception {
		Map<Integer, Integer> mapping = PreservationTransportNormalizer.mapping(info(0x1011, 0x1100),
				info(0x1011, 0x1102), List.of(entry(0x1011, 0x1b), entry(0x1102, 0x81)));
		assertThat(mapping).containsEntry(0x1100, 0x1102).containsEntry(0x1001, 0x1001);
	}

	@Test
	void realPatNetworkEntryAndSitPidArePreservedWithoutAllowingReservedElementaryPids() throws Exception {
		M2tsInfo generated = info(0x1011, 0x1100);
		M2tsInfo source = info(0x1011, 0x1102);
		generated.setSitPid(0x1f);
		source.setSitPid(0x1f);
		Map<Integer, Integer> mapping = PreservationTransportNormalizer.mapping(generated, source,
				List.of(entry(0x1011, 0x1b), entry(0x1102, 0x81)));
		assertThat(mapping).containsEntry(0x1f, 0x1f);
		List<byte[]> data = fixture(0x05);
		data.removeIf(p -> pid(p) == 0);
		byte[] pat = { 0, (byte) 0xb0, 17, 0, 1, (byte) 0xc1, 0, 0, 0, 0, (byte) 0xe0, 0x1f, 0, 1, (byte) 0xe1, 0, 0, 0,
				0, 0 };
		finishCrc(pat);
		byte[] sit = { 0x7f, (byte) 0xf0, 11, (byte) 0xff, (byte) 0xff, (byte) 0xc1, 0, 0, (byte) 0xf0, 0, 0, 0, 0, 0 };
		finishCrc(sit);
		// Append with a constant later ATS so fixture packet removal does not reorder clocks.
		addSection(data, 0, pat, pat.length);
		addSection(data, 0x1f, sit, sit.length);
		for (int i = 0; i < data.size(); i++)
			PreservationTransportNormalizer.write32(data.get(i), 0,
					Math.floorMod((WRAP - 20_000) * 300 + i * 1000L, 1L << 30));
		Path output = temp.resolve("sit-out.m2ts");
		new PreservationTransportNormalizer(mapping, Map.of(0x1011, 10_000L, 0x1100, 10_000L), 10_000, 0x100, 0x1001,
				Set.of(0x1011, 0x1100)).normalize(write(data, "sit.m2ts"), output);
		assertThat(section(packets(output), 0x1f)).containsExactly(sit);
		assertThat(section(packets(output), 0)).containsExactly(pat);
		assertThatThrownBy(() -> PreservationTransportNormalizer.mapping(generated, source,
				List.of(entry(0x1011, 0x1b), entry(0x1f, 0x81)))).isInstanceOf(IOException.class)
				.hasMessageContaining("Reserved target elementary");
		generated.getStreams().get(1).setPid(0x1f);
		assertThatThrownBy(() -> PreservationTransportNormalizer.mapping(generated, source,
				List.of(entry(0x1011, 0x1b), entry(0x1102, 0x81)))).isInstanceOf(IOException.class)
				.hasMessageContaining("Invalid control PID");
	}

	@Test
	void shiftsSplitBluRayAudioPesWhilePreservingExtendedStreamIdAndPayload() throws Exception {
		byte[] extended = extendedAudioPes(new byte[] { 0x01, (byte) 0x81, 0x71 });
		List<byte[]> data = withAudioPes(extended);
		Path output = temp.resolve("extension-out.m2ts");
		normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_001L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
				.normalize(write(data, "extension.m2ts"), output);
		byte[] actual = payloads(packets(output), 0x1102);
		assertThat(actual[7] & 255).isEqualTo(0x81);
		assertThat(PreservationTransportNormalizer.readTimestamp(actual, 9)).isEqualTo(4_001);
		assertThat(Arrays.copyOfRange(actual, 14, actual.length))
				.containsExactly(Arrays.copyOfRange(extended, 14, extended.length));
	}

	@Test
	void preservesStandardClockFreeExtensionFieldsAndHeaderStuffing() throws Exception {
		byte[] extension = new byte[26];
		extension[0] = (byte) 0xb1; // private data, sequence counter, P-STD buffer, extended stream ID
		Arrays.fill(extension, 1, 17, (byte) 0x55);
		extension[17] = (byte) 0x80;
		extension[18] = (byte) 0x80;
		extension[19] = 0x40;
		extension[20] = 0;
		extension[21] = (byte) 0x81;
		extension[22] = 0x71;
		Arrays.fill(extension, 23, 26, (byte) 0xff);
		byte[] audio = extendedAudioPes(extension);
		Path output = temp.resolve("standard-extension-out.m2ts");
		normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_000L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
				.normalize(write(withAudioPes(audio), "standard-extension.m2ts"), output);
		assertThat(Arrays.copyOfRange(payloads(packets(output), 0x1102), 14, audio.length))
				.containsExactly(Arrays.copyOfRange(audio, 14, audio.length));
	}

	@Test
	void rejectsUnsupportedPesClocksAndMalformedExtensions() throws Exception {
		List<byte[]> invalid = new ArrayList<>();
		for (int flag : new int[] { 0x20, 0x10, 0x08, 0x04, 0x02 }) {
			byte[] audio = pes(WRAP - 6_000, null);
			audio[7] |= (byte) flag;
			invalid.add(audio);
		}
		invalid.add(extendedAudioPes(new byte[] { 0x40 })); // pack header may carry SCR
		invalid.add(extendedAudioPes(new byte[] { 1, (byte) 0x86, (byte) 0xfe })); // truncated extension-2
		invalid.add(extendedAudioPes(new byte[] { 1, (byte) 0x81, (byte) 0xfe })); // TREF
		invalid.add(extendedAudioPes(new byte[] { 1, 1, 0x71 })); // missing marker
		invalid.add(extendedAudioPes(new byte[] { (byte) 0x80 })); // truncated private data
		for (int i = 0; i < invalid.size(); i++) {
			Path input = write(withAudioPes(invalid.get(i)), "invalid-extension-" + i + ".m2ts");
			Path output = temp.resolve("invalid-out-" + i + ".m2ts");
			assertThatThrownBy(
					() -> normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_000L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
							.normalize(input, output))
					.isInstanceOf(IOException.class).hasMessageContaining("PES");
		}
	}

	private List<byte[]> withAudioPes(byte[] audio) {
		List<byte[]> data = fixture(0x05);
		data.removeIf(p -> pid(p) == 0x1100);
		addPes(data, 0x1100, audio);
		return data;
	}

	private static byte[] extendedAudioPes(byte[] extension) {
		byte[] original = pes(WRAP - 6_000, null);
		byte[] result = Arrays.copyOf(original, original.length + extension.length);
		System.arraycopy(original, 14, result, 14 + extension.length, original.length - 14);
		System.arraycopy(extension, 0, result, 14, extension.length);
		result[5] = (byte) (result.length - 6);
		result[7] = (byte) 0x81;
		result[8] += (byte) extension.length;
		return result;
	}

	@Test
	void rejectsUnknownPidBearingDescriptorAcrossPackets() throws Exception {
		Path input = write(fixture(0x7f), "unknown.m2ts");
		assertThatThrownBy(
				() -> normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_000L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
						.normalize(input, temp.resolve("bad.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("descriptor tag");
	}

	@Test
	void rejectsNegativeDelayThatMissesDecodeDeadline() throws Exception {
		Path input = write(fixture(0x05), "unsafe.m2ts");
		assertThatThrownBy(
				() -> normalizer(Map.of(0x1011, 10_000L, 0x1100, -90_000L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
						.normalize(input, temp.resolve("bad.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("Unsafe shifted");
	}

	@Test
	void rejectsTruncatedPsiAndInvalidCrc() throws Exception {
		List<byte[]> data = fixture(0x05);
		data.get(2)[191] ^= 1;
		Path input = write(data, "bad-crc.m2ts");
		assertThatThrownBy(
				() -> normalizer(Map.of(0x1011, 10_000L, 0x1100, 10_000L), Map.of(0x1011, 0x1011, 0x1100, 0x1102))
						.normalize(input, temp.resolve("bad.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("CRC");
		Path truncated = write(fixture(0x05).subList(0, 2), "truncated.m2ts");
		assertThatThrownBy(
				() -> normalizer(Map.of(), Map.of()).normalize(truncated, temp.resolve("truncated-out.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("Truncated PSI");
	}

	@Test
	void rejectsContinuityAndTransportDiscontinuity() throws Exception {
		List<byte[]> data = fixture(0x05);
		data.get(2)[7] ^= 3;
		Path input = write(data, "bad-cc.m2ts");
		assertThatThrownBy(() -> normalizer(Map.of(), Map.of()).normalize(input, temp.resolve("bad.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("Continuity");
		List<byte[]> discontinuous = fixture(0x05);
		discontinuous.getFirst()[9] |= (byte) 0x80;
		Path second = write(discontinuous, "discontinuous.m2ts");
		assertThatThrownBy(() -> normalizer(Map.of(), Map.of()).normalize(second, temp.resolve("bad2.m2ts")))
				.isInstanceOf(IOException.class).hasMessageContaining("discontinuity");
	}

	private PreservationTransportNormalizer normalizer(Map<Integer, Long> shifts, Map<Integer, Integer> streams) {
		Map<Integer, Integer> pids = new HashMap<>(Map.of(0, 0, 0x100, 0x100, 0x1001, 0x1001, 0x1fff, 0x1fff));
		pids.putAll(streams);
		return new PreservationTransportNormalizer(pids, shifts, 10_000, 0x100, 0x1001, Set.of(0x1011, 0x1100));
	}

	private static M2tsInfo info(int video, int audio) {
		M2tsInfo info = new M2tsInfo();
		info.setPmtPid(0x100);
		info.setPcrPid(0x1001);
		info.setProgramPidMap(Map.of(1, 0x100));
		M2tsStreamInfo v = new M2tsStreamInfo();
		v.setPid(video);
		v.setStreamTypeByte(0x1b);
		M2tsStreamInfo a = new M2tsStreamInfo();
		a.setPid(audio);
		a.setStreamTypeByte(0x81);
		info.setStreams(List.of(v, a));
		return info;
	}

	private static M2tsDescriptor.StreamEntry entry(int pid, int type) {
		var result = new M2tsDescriptor.StreamEntry();
		result.setPid(pid);
		result.setStreamTypeByte(type);
		return result;
	}

	private List<byte[]> fixture(int descriptorTag) {
		List<byte[]> packets = new ArrayList<>();
		byte[] pcr = packet(0x1001, false, 0, new byte[0], 0);
		pcr[7] = 0x20;
		pcr[8] = (byte) 183;
		pcr[9] = 0x10;
		long base = WRAP - 20_000;
		pcr[10] = (byte) (base >>> 25);
		pcr[11] = (byte) (base >>> 17);
		pcr[12] = (byte) (base >>> 9);
		pcr[13] = (byte) (base >>> 1);
		pcr[14] = (byte) ((base << 7) | 0x7e);
		pcr[15] = 0;
		packets.add(pcr);
		byte[] pat = new byte[] { 0, (byte) 0xb0, 13, 0, 1, (byte) 0xc1, 0, 0, 0, 1, (byte) 0xe1, 0, 0, 0, 0, 0 };
		finishCrc(pat);
		addSection(packets, 0, pat, 8);
		byte[] pmt = new byte[12 + 240 + 10 + 4];
		pmt[0] = 2;
		pmt[1] = (byte) (0xb0 | ((pmt.length - 3) >>> 8));
		pmt[2] = (byte) (pmt.length - 3);
		pmt[4] = 1;
		pmt[5] = (byte) 0xc1;
		pmt[8] = (byte) 0xf0;
		pmt[9] = 1;
		pmt[10] = (byte) 0xf0;
		pmt[11] = (byte) 240;
		for (int i = 12; i < 252; i += 6) {
			pmt[i] = (byte) descriptorTag;
			pmt[i + 1] = 4;
			pmt[i + 2] = 'H';
			pmt[i + 3] = 'D';
			pmt[i + 4] = 'M';
			pmt[i + 5] = 'V';
		}
		for (int i = 0; i < 2; i++) {
			int at = 252 + i * 5;
			int pid = i == 0 ? 0x1011 : 0x1100;
			pmt[at] = (byte) (i == 0 ? 0x1b : 0x81);
			pmt[at + 1] = (byte) (0xe0 | (pid >>> 8));
			pmt[at + 2] = (byte) pid;
			pmt[at + 3] = (byte) 0xf0;
		}
		finishCrc(pmt);
		addSection(packets, 0x100, pmt, 80);
		addPes(packets, 0x1011, pes(WRAP - 5_000, WRAP - 8_000));
		addPes(packets, 0x1100, pes(WRAP - 6_000, null));
		return packets;
	}

	private static byte[] pes(long pts, Long dts) {
		int header = dts == null ? 14 : 19;
		byte[] result = new byte[header + 6];
		result[2] = 1;
		result[3] = (byte) (dts == null ? 0xfd : 0xe0);
		result[5] = (byte) (result.length - 6);
		result[6] = (byte) 0x80;
		result[7] = (byte) (dts == null ? 0x80 : 0xc0);
		result[8] = (byte) (header - 9);
		result[9] = (byte) (dts == null ? 0x20 : 0x30);
		PreservationTransportNormalizer.writeTimestamp(result, 9, pts);
		if (dts != null) {
			result[14] = 0x10;
			PreservationTransportNormalizer.writeTimestamp(result, 14, dts);
		}
		for (int i = 0; i < 6; i++)
			result[header + i] = (byte) (i + 1);
		return result;
	}

	private static void addPes(List<byte[]> packets, int pid, byte[] pes) {
		packets.add(packet(pid, true, 0, Arrays.copyOfRange(pes, 0, 10), packets.size()));
		packets.add(packet(pid, false, 1, Arrays.copyOfRange(pes, 10, pes.length), packets.size()));
	}

	private static void addSection(List<byte[]> packets, int pid, byte[] section, int firstCount) {
		int offset = 0;
		int cc = 0;
		while (offset < section.length) {
			int count = Math.min(offset == 0 ? firstCount : 80, section.length - offset);
			byte[] payload = new byte[count + (offset == 0 ? 1 : 0)];
			System.arraycopy(section, offset, payload, offset == 0 ? 1 : 0, count);
			packets.add(packet(pid, offset == 0, cc++, payload, packets.size()));
			offset += count;
		}
	}

	private static byte[] packet(int pid, boolean start, int cc, byte[] payload, int index) {
		byte[] packet = new byte[192];
		Arrays.fill(packet, (byte) 0xff);
		PreservationTransportNormalizer.write32(packet, 0,
				Math.floorMod((WRAP - 20_000) * 300 + index * 1000L, 1L << 30));
		packet[4] = 0x47;
		packet[5] = (byte) ((start ? 0x40 : 0) | (pid >>> 8));
		packet[6] = (byte) pid;
		packet[7] = (byte) (0x30 | cc);
		packet[8] = (byte) (183 - payload.length);
		packet[9] = 0;
		System.arraycopy(payload, 0, packet, 192 - payload.length, payload.length);
		return packet;
	}

	private static void finishCrc(byte[] section) {
		PreservationTransportNormalizer.write32(section, section.length - 4,
				PreservationTransportNormalizer.crc(section, section.length - 4) & 0xffffffffL);
	}

	private Path write(List<byte[]> packets, String name) throws IOException {
		Path path = temp.resolve(name);
		try (var out = Files.newOutputStream(path)) {
			for (byte[] packet : packets)
				out.write(packet);
		}
		return path;
	}

	private static List<byte[]> packets(Path path) throws IOException {
		byte[] bytes = Files.readAllBytes(path);
		List<byte[]> result = new ArrayList<>();
		for (int offset = 0; offset < bytes.length; offset += 192)
			result.add(Arrays.copyOfRange(bytes, offset, offset + 192));
		return result;
	}

	private static int pid(byte[] packet) {
		return ((packet[5] & 31) << 8) | (packet[6] & 255);
	}

	private static byte[] payloads(List<byte[]> packets, int pid) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		for (byte[] packet : packets)
			if (pid(packet) == pid && (packet[7] & 0x10) != 0) {
				int offset = (packet[7] & 0x20) == 0 ? 8 : 9 + (packet[8] & 255);
				result.write(packet, offset, 192 - offset);
			}
		return result.toByteArray();
	}

	private static byte[] section(List<byte[]> packets, int pid) throws IOException {
		byte[] payload = payloads(packets, pid);
		return Arrays.copyOfRange(payload, 1, payload.length);
	}
}
