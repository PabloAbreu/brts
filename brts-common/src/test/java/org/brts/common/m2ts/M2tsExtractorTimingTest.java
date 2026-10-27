/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/M2tsExtractorTimingTest.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsExtractorTimingTest {
	@TempDir
	Path temp;

	@Test
	void sourceDigestBindsManifestAndLegacyJsonRemainsReadable() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1, 2 }, 123L, null, false), 8);
		Path out = extract(fixture, "fingerprinted", true);
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.sourceSha256()).isEqualTo(SourceFingerprint.sha256(temp.resolve("source.m2ts")));
		assertThat(manifest.sourceSize()).isEqualTo(Files.size(temp.resolve("source.m2ts")));
		var legacyJson = JsonMapperFactory.get().valueToTree(manifest);
		((com.fasterxml.jackson.databind.node.ObjectNode) legacyJson).remove("sourceSha256");
		assertThat(JsonMapperFactory.get().treeToValue(legacyJson, M2tsTimingManifest.class).sourceSha256()).isNull();
	}

	@Test
	void expectedDigestRejectsSameSizeMutationBeforeAnyWritesForBothModes() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1, 2 }, 123L, null, false), 8);
		Path source = temp.resolve("mutated.m2ts");
		byte[] bytes = fixture.bytes.toByteArray();
		Files.write(source, bytes);
		String expected = SourceFingerprint.sha256(source);
		bytes[0] ^= 1;
		Files.write(source, bytes);
		for (boolean preserve : List.of(false, true)) {
			Path out = temp.resolve("untouched-" + preserve);
			assertThatThrownBy(() -> new M2tsExtractor().extract(source, fixture.info, out, null, preserve, expected))
					.isInstanceOf(IOException.class).hasMessageContaining("SHA-256 mismatch");
			assertThat(out).doesNotExist();
		}
	}

	@Test
	void rejectedFingerprintLeavesExistingExtractionAndManifestUntouched() throws Exception {
		Path source = Files.writeString(temp.resolve("stale.m2ts"), "changed");
		Path out = Files.createDirectory(temp.resolve("existing"));
		Path es = Files.writeString(out.resolve("pid_1100.ac3"), "old media");
		Path manifest = Files.writeString(out.resolve("timing.json"), "old manifest");
		assertThatThrownBy(() -> new M2tsExtractor().extract(source, new M2tsInfo(), out, null, true, "0".repeat(64)))
				.isInstanceOf(IOException.class).hasMessageContaining("SHA-256 mismatch");
		assertThat(Files.readString(es)).isEqualTo("old media");
		assertThat(Files.readString(manifest)).isEqualTo("old manifest");
	}

	@Test
	void fingerprintCoversUnselectedTransportPackets() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1 }, 123L, null, false), 8);
		fixture.pes(0x1102, pes(new byte[] { 2 }, 123L, null, false), 8);
		Path source = Files.write(temp.resolve("all-packets.m2ts"), fixture.bytes.toByteArray());
		Path out = temp.resolve("one-pid");
		new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1100), true);
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.sourceSha256()).isEqualTo(SourceFingerprint.sha256(source));
		assertThat(manifest.streams()).extracting(s -> s.pid()).containsExactly(0x1100);
	}

	@Test
	void matchingExpectedDigestExtractsAndPublishesIdentity() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 7 }, 123L, null, false), 8);
		Path source = temp.resolve("matching.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		String expected = SourceFingerprint.sha256(source);
		Path out = temp.resolve("matching");
		new M2tsExtractor().extract(source, fixture.info, out, null, true, expected);
		assertThat(M2tsTiming.read(out.resolve("timing.json")).sourceSha256()).isEqualTo(expected);
	}

	@Test
	void splitPesHeaderAtEveryPositionAndExcludeBoundedStuffing() throws Exception {
		byte[] elementary = { 1, 2, (byte) 0xFF, 3 };
		byte[] pes = pes(elementary, 123456L, 123000L, false);
		for (int split = 1; split < 19; split++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			fixture.packet(0x1100, Arrays.copyOf(pes, split), true, false);
			fixture.packet(0x1100, Arrays.copyOfRange(pes, split, pes.length), false, true);
			Path out = extract(fixture, "split-" + split, true);
			assertThat(Files.readAllBytes(out.resolve("pid_1100.ac3"))).containsExactly(elementary);
			var timing = M2tsTiming.read(out.resolve("timing.json")).streams().getFirst();
			assertThat(timing.firstPts90k()).isEqualTo(123456L);
			assertThat(timing.firstDts90k()).isEqualTo(123000L);
			assertThat(timing.pesTimings()).hasSize(1);
			assertThat(timing.pesTimings().getFirst().packetIndex()).isZero();
		}
	}

	@Test
	void pgsSegmentsSpanPesAndTsBoundariesAndKeepFullAbsoluteTiming() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		byte[] body = new byte[300];
		for (int i = 0; i < body.length; i++)
			body[i] = (byte) i;
		byte[] segment = concat(new byte[] { 0x15, 1, 44 }, body);
		long pts = (1L << 32) + 900000;
		long dts = pts - 90;
		// Even the 3-byte PGS segment header straddles PES boundaries.
		fixture.pes(0x1200, pes(Arrays.copyOf(segment, 2), pts, dts, false), 5);
		fixture.pes(0x1200,
				pes(concat(Arrays.copyOfRange(segment, 2, segment.length), new byte[] { (byte) 0x80, 0, 0 }),
						pts + 9000, dts + 9000, false),
				7);
		Path out = extract(fixture, "pgs", true);
		byte[] sup = Files.readAllBytes(out.resolve("pid_1200.pgs"));
		try (DataInputStream records = new DataInputStream(new ByteArrayInputStream(sup))) {
			assertSup(records, pts, dts, 0x15, body);
			assertSup(records, pts + 9000, dts + 9000, 0x80, new byte[0]);
			assertThat(records.available()).isZero();
		}
		M2tsTimingManifest manifest = M2tsTiming.read(out.resolve("timing.json"));
		var timing = manifest.streams().getFirst();
		assertThat(timing.fileName()).isEqualTo("pid_1200.pgs");
		assertThat(timing.segments()).hasSize(2);
		assertThat(timing.segments().getFirst().pts90k()).isEqualTo(pts);
		assertThat(timing.segments().getFirst().dts90k()).isEqualTo(dts);
		assertThat(timing.segments().get(1).outputOffset()).isEqualTo(313);
		assertThat(timing.segments().get(1).pts90k()).isEqualTo(pts + 9000);
		assertThat(JsonMapperFactory.get().readValue(JsonMapperFactory.get().writeValueAsBytes(manifest),
				M2tsTimingManifest.class)).isEqualTo(manifest);
	}

	@Test
	void unwrapsAcrossPidsAndKeepsPtsAndDtsInSameEpoch() throws Exception {
		long wrap = M2tsTiming.TIMESTAMP_WRAP;
		Fixture fixture = new Fixture(stream(0x1011, StreamCodingType.H264_AVC),
				stream(0x1102, StreamCodingType.DOLBY_AC3), stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		fixture.pes(0x1011, pes(new byte[] { 1 }, wrap - 100, wrap - 200, true), 4);
		fixture.pes(0x1011, pes(new byte[] { 2 }, 100L, 0L, true), 6);
		fixture.pes(0x1102, pes(new byte[] { 3 }, 50L, null, false), 3);
		fixture.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, 200L, 190L, false), 9);
		Path out = extract(fixture, "wrap", true);
		var timings = M2tsTiming.read(out.resolve("timing.json")).streams();
		assertThat(timings.get(0).pesTimings().get(1).pts90k()).isEqualTo(wrap + 100);
		assertThat(timings.get(0).pesTimings().get(1).dts90k()).isEqualTo(wrap);
		assertThat(timings.get(1).firstPts90k()).isEqualTo(wrap + 50);
		assertThat(timings.get(1).firstDts90k()).isNull();
		assertThat(timings.get(2).segments().getFirst().pts90k()).isEqualTo(wrap + 200);
		assertThat(timings.get(2).segments().getFirst().dts90k()).isEqualTo(wrap + 190);
	}

	@Test
	void pcrAnchorsLateFirstSelectedStreamAfterWrap() throws Exception {
		long wrap = M2tsTiming.TIMESTAMP_WRAP;
		Fixture fixture = new Fixture(stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.info.setPcrPid(0x1001);
		fixture.pcr(wrap - 200);
		fixture.pcr(10);
		fixture.pes(0x1102, pes(new byte[] { 7 }, 100L, null, false), 8);
		Path out = extract(fixture, "pcr", true);
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.firstPcr90k()).isEqualTo(wrap - 200);
		assertThat(manifest.streams().getFirst().firstPts90k()).isEqualTo(wrap + 100);
		assertThat(Files.readAllBytes(out.resolve("pid_1102.ac3"))).containsExactly((byte) 7);
	}

	@Test
	void absentPgsDtsUsesSupZeroButManifestRetainsAbsence() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		fixture.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, 987654L, null, false), 2);
		Path out = extract(fixture, "no-dts", true);
		try (DataInputStream in = new DataInputStream(Files.newInputStream(out.resolve("pid_1200.pgs")))) {
			assertSup(in, 987654L, 0L, 0x80, new byte[0]);
		}
		var timing = M2tsTiming.read(out.resolve("timing.json")).streams().getFirst();
		assertThat(timing.firstDts90k()).isNull();
		assertThat(timing.segments().getFirst().dts90k()).isNull();
	}

	@Test
	void legacyExtractionAndFalseOverloadRemainRawIncludingLegacyStuffing() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		byte[] raw = { (byte) 0x80, 0, 0 };
		byte[] pes = pes(raw, 500L, null, false);
		fixture.packet(0x1200, pes, true, true);
		Path source = temp.resolve("legacy.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		Path legacy = temp.resolve("legacy-out");
		new M2tsExtractor().extract(source, fixture.info, legacy);
		Path explicitFalse = temp.resolve("false-out");
		new M2tsExtractor().extract(source, fixture.info, explicitFalse, Set.of(0x1200), false);
		byte[] expected = new byte[184 - 14];
		Arrays.fill(expected, (byte) 0xFF);
		System.arraycopy(raw, 0, expected, 0, raw.length);
		assertThat(Files.readAllBytes(legacy.resolve("pid_1200.pgs"))).containsExactly(expected);
		assertThat(Files.readAllBytes(explicitFalse.resolve("pid_1200.pgs"))).containsExactly(expected);
		assertThat(legacy.resolve("timing.json")).doesNotExist();
		assertThat(explicitFalse.resolve("timing.json")).doesNotExist();
	}

	@Test
	void keepsOnlyRequestedSparsePidAndRejectsUnknownPid() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1 }, 10L, null, false), 3);
		fixture.pes(0x1102, pes(new byte[] { 2 }, 20L, null, false), 3);
		Path source = temp.resolve("selected.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		Path out = temp.resolve("selected");
		new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1102), true);
		assertThat(out.resolve("pid_1100.ac3")).doesNotExist();
		assertThat(M2tsTiming.read(out.resolve("timing.json")).streams()).extracting(s -> s.pid())
				.containsExactly(0x1102);
		assertThatThrownBy(() -> new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1103), true))
				.isInstanceOf(IOException.class).hasMessageContaining("requested PID");
		assertThat(out.resolve("timing.json")).doesNotExist();
	}

	@Test
	void malformedHeadersAndTruncatedPesFailWithoutManifestEvenAfterSuccessfulRun() throws Exception {
		byte[] good = pes(new byte[] { 1, 2, 3 }, 12345L, null, false);
		for (int malformed = 0; malformed < 8; malformed++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			byte[] bad = good.clone();
			switch (malformed) {
			case 0 -> bad[9] &= (byte) 0xFE; // broken PTS marker
			case 1 -> bad[9] = (byte) ((bad[9] & 0x0F) | 0x30); // wrong PTS prefix
			case 2 -> bad[7] = 0x40; // illegal PTS_DTS flags
			case 3 -> bad[8] = 4; // insufficient timestamp header bytes
			case 4 -> bad[5] = 1; // declared length smaller than header
			case 5 -> bad = Arrays.copyOf(bad, 10); // truncated optional header
			case 6 -> bad = Arrays.copyOf(bad, bad.length - 1); // truncated bounded payload
			case 7 -> bad = concat(bad, new byte[] { 42 }); // nonstuffing tail
			default -> throw new AssertionError();
			}
			fixture.packet(0x1100, bad, true, false);
			Path out = temp.resolve("malformed-" + malformed);
			Files.createDirectories(out);
			Files.writeString(out.resolve("timing.json"), "old-success");
			assertThatThrownBy(() -> extractTo(fixture, out, true)).isInstanceOf(IOException.class);
			assertThat(out.resolve("timing.json")).doesNotExist();
		}
	}

	@Test
	void unfinishedPgsSegmentAndMissingTimingAreRejected() throws Exception {
		for (byte[] payload : List.of(new byte[] { 0x15, 0 }, new byte[] { 0x15, 0, 5, 1 },
				new byte[] { 0x55, 0, 0 })) {
			Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
			fixture.pes(0x1200, pes(payload, 10L, null, false), 4);
			assertThatThrownBy(() -> extract(fixture, "incomplete-pgs", true)).isInstanceOf(IOException.class);
			assertThat(temp.resolve("incomplete-pgs/timing.json")).doesNotExist();
		}
		Fixture untimed = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		untimed.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, null, null, false), 3);
		assertThatThrownBy(() -> extract(untimed, "untimed", true)).isInstanceOf(IOException.class)
				.hasMessageContaining("no source PTS");
	}

	@Test
	void backwardsDtsAndTransportErrorsFailClosed() throws Exception {
		Fixture backwards = new Fixture(stream(0x1011, StreamCodingType.H264_AVC));
		backwards.pes(0x1011, pes(new byte[] { 1 }, 1000L, 900L, false), 184);
		backwards.pes(0x1011, pes(new byte[] { 2 }, 1100L, 800L, false), 184);
		assertThatThrownBy(() -> extract(backwards, "backwards", true)).isInstanceOf(IOException.class)
				.hasMessageContaining("Backward DTS");
		for (int malformed = 0; malformed < 6; malformed++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			fixture.pes(0x1100, pes(new byte[] { 1 }, 1000L, null, false), 7);
			byte[] bytes = fixture.bytes.toByteArray();
			switch (malformed) {
			case 0 -> bytes[5] |= (byte) 0x80; // TEI
			case 1 -> bytes[7] |= (byte) 0x80; // scrambling
			case 2 -> bytes[9] |= (byte) 0x80; // discontinuity
			case 3 -> bytes[192 + 7] = (byte) ((bytes[192 + 7] & 0xF0) | 7); // lost packet
			case 4 -> bytes[4] = 0; // sync
			case 5 -> bytes = Arrays.copyOf(bytes, bytes.length - 1); // partial TS
			default -> throw new AssertionError();
			}
			fixture.bytes.reset();
			fixture.bytes.write(bytes);
			String directory = "bad-transport-" + malformed;
			assertThatThrownBy(() -> extract(fixture, directory, true)).isInstanceOf(IOException.class);
			assertThat(temp.resolve(directory).resolve("timing.json")).doesNotExist();
		}
	}

	@Test
	void unwrapHelperRejectsAmbiguousEpochAndManifestRejectsUnsupportedVersion() throws Exception {
		assertThat(M2tsTiming.unwrap33(5, M2tsTiming.TIMESTAMP_WRAP - 5)).isEqualTo(M2tsTiming.TIMESTAMP_WRAP + 5);
		assertThatThrownBy(() -> M2tsTiming.unwrap33(1L << 32, 0)).isInstanceOf(IllegalArgumentException.class);
		Path invalid = temp.resolve("unsupported.json");
		Files.writeString(invalid, "{\"version\":999}");
		assertThatThrownBy(() -> M2tsTiming.read(invalid)).isInstanceOf(IOException.class);
	}

	private Path extract(Fixture fixture, String directory, boolean preserve) throws IOException {
		Path out = temp.resolve(directory);
		extractTo(fixture, out, preserve);
		return out;
	}

	private void extractTo(Fixture fixture, Path out, boolean preserve) throws IOException {
		Path source = temp.resolve("source.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		new M2tsExtractor().extract(source, fixture.info, out, preserve);
	}

	private static void assertSup(DataInputStream in, long pts, long dts, int type, byte[] data) throws IOException {
		assertThat(in.readUnsignedShort()).isEqualTo(0x5047);
		assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(pts & 0xFFFF_FFFFL);
		assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(dts & 0xFFFF_FFFFL);
		assertThat(in.readUnsignedByte()).isEqualTo(type);
		assertThat(in.readUnsignedShort()).isEqualTo(data.length);
		assertThat(in.readNBytes(data.length)).containsExactly(data);
	}

	private static M2tsStreamInfo stream(int pid, StreamCodingType type) {
		M2tsStreamInfo stream = new M2tsStreamInfo();
		stream.setPid(pid);
		stream.setCodingType(type);
		return stream;
	}

	private static byte[] pes(byte[] data, Long pts, Long dts, boolean unbounded) {
		int optional = pts == null ? 0 : dts == null ? 5 : 10;
		byte[] result = new byte[9 + optional + data.length];
		result[2] = 1;
		result[3] = (byte) (unbounded ? 0xE0 : 0xBD);
		int size = unbounded ? 0 : 3 + optional + data.length;
		result[4] = (byte) (size >> 8);
		result[5] = (byte) size;
		result[6] = (byte) 0x80;
		result[7] = (byte) (pts == null ? 0 : dts == null ? 0x80 : 0xC0);
		result[8] = (byte) optional;
		if (pts != null)
			System.arraycopy(timestamp(pts, dts == null ? 2 : 3), 0, result, 9, 5);
		if (dts != null)
			System.arraycopy(timestamp(dts, 1), 0, result, 14, 5);
		System.arraycopy(data, 0, result, 9 + optional, data.length);
		return result;
	}

	private static byte[] timestamp(long value, int prefix) {
		return new byte[] { (byte) ((prefix << 4) | ((value >> 29) & 0x0E) | 1), (byte) (value >> 22),
				(byte) (((value >> 14) & 0xFE) | 1), (byte) (value >> 7), (byte) (((value << 1) & 0xFE) | 1) };
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private static final class Fixture {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final M2tsInfo info = new M2tsInfo();
		final Map<Integer, Integer> continuity = new HashMap<>();

		Fixture(M2tsStreamInfo... streams) {
			info.setStreams(List.of(streams));
		}

		void pes(int pid, byte[] pes, int firstChunk) {
			int offset = 0;
			while (offset < pes.length) {
				int size = Math.min(offset == 0 ? firstChunk : 184, pes.length - offset);
				packet(pid, Arrays.copyOfRange(pes, offset, offset + size), offset == 0, false);
				offset += size;
			}
		}

		void packet(int pid, byte[] payload, boolean start, boolean stuffing) {
			byte[] packet = new byte[192];
			Arrays.fill(packet, (byte) 0xFF);
			Arrays.fill(packet, 0, 4, (byte) 0);
			packet[4] = 0x47;
			packet[5] = (byte) ((start ? 0x40 : 0) | (pid >> 8));
			packet[6] = (byte) pid;
			int cc = continuity.getOrDefault(pid, 0);
			continuity.put(pid, (cc + 1) & 15);
			int offset = 8;
			if (stuffing || payload.length == 184) {
				packet[7] = (byte) (0x10 | cc);
			} else {
				packet[7] = (byte) (0x30 | cc);
				int afLength = 183 - payload.length;
				packet[8] = (byte) afLength;
				if (afLength > 0)
					packet[9] = 0;
				offset = 9 + afLength;
			}
			System.arraycopy(payload, 0, packet, offset, payload.length);
			bytes.writeBytes(packet);
		}

		void pcr(long base) {
			byte[] packet = new byte[192];
			Arrays.fill(packet, (byte) 0xFF);
			Arrays.fill(packet, 0, 4, (byte) 0);
			int pid = info.getPcrPid();
			packet[4] = 0x47;
			packet[5] = (byte) (pid >> 8);
			packet[6] = (byte) pid;
			packet[7] = 0x20;
			packet[8] = (byte) 183;
			packet[9] = 0x10;
			packet[10] = (byte) (base >> 25);
			packet[11] = (byte) (base >> 17);
			packet[12] = (byte) (base >> 9);
			packet[13] = (byte) (base >> 1);
			packet[14] = (byte) (((base & 1) << 7) | 0x7E);
			packet[15] = 0;
			bytes.writeBytes(packet);
		}
	}
}
