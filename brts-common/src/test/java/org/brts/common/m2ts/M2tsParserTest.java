/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/M2tsParserTest.java' is part of BRTS.
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsParserTest {

	private static final int PMT_PID = 0x100;
	private static final int PCR_PID = 0x1001;
	private static final int AUDIO_PID = 0x1107;
	private static final byte[] AC3_DESCRIPTORS = bytes(0x05, 4, 'A', 'C', '-', '3', 0x81, 4, 0x06, 0x48, 0x0e, 0x00,
			0x0a, 4, 'e', 'n', 'g', 0);

	@TempDir
	Path tempDir;

	private int fixtureNumber;

	@Test
	void singlePacketPmtPreservesMetadataWithAdaptationOrPayloadStuffing() throws IOException {
		for (boolean payloadOnly : new boolean[] { false, true }) {
			M2tsInfo info = parse(List.of(pat(false), packet(PMT_PID, 0, true, start(smallPmt()), payloadOnly)));
			assertThat(info.getTotalPackets()).isEqualTo(2);
			assertThat(info.getPmtPid()).isEqualTo(PMT_PID);
			assertThat(info.getPcrPid()).isEqualTo(PCR_PID);
			assertThat(info.getProgramPidMap()).isEqualTo(Map.of(1, PMT_PID));
			assertAudio(info, AUDIO_PID);
		}
	}

	@Test
	void twoPacketPmtPublishesAllThirtyOneStreamsInOrder() throws IOException {
		byte[][] streams = new byte[31][];
		streams[0] = es(0x81, AUDIO_PID, AC3_DESCRIPTORS);
		for (int i = 1; i < streams.length; i++) {
			streams[i] = es(0x90, 0x1200 + i, new byte[0]);
		}
		byte[] section = pmt(1, PCR_PID, streams);
		List<byte[]> packets = packets(section, PMT_PID, 0, 183);
		assertThat(packets).hasSize(2);
		packets.add(0, pat(false));
		M2tsInfo info = parse(packets);
		assertThat(info.getPcrPid()).isEqualTo(PCR_PID);
		assertThat(info.getStreams()).extracting(M2tsStreamInfo::getPid).containsExactlyElementsOf(
				IntStream.range(0, 31).map(i -> i == 0 ? AUDIO_PID : 0x1200 + i).boxed().toList());
		assertAudio(info, AUDIO_PID);
		assertThat(info.getStreams().get(30).getCodingType()).isEqualTo(StreamCodingType.PRESENTATION_GRAPHICS);
	}

	@Test
	void reassemblesEverySmallSectionSplitIncludingHeaderDescriptorsAndCrc() throws IOException {
		byte[] section = smallPmt();
		for (int split = 1; split < section.length; split++) {
			M2tsInfo info = parse(
					List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, split)), false),
							packet(PMT_PID, 1, false, Arrays.copyOfRange(section, split, section.length), false)));
			assertThat(info.getStreams()).as("section split at byte %s", split).hasSize(1);
			assertThat(info.getPcrPid()).isEqualTo(PCR_PID);
			assertAudio(info, AUDIO_PID);
		}
	}

	@Test
	void reassemblesLargeSectionAcrossMoreThanThreePacketsWithInterleavedTraffic() throws IOException {
		byte[][] streams = IntStream.range(0, 31).mapToObj(i -> es(0x81, 0x1100 + i, AC3_DESCRIPTORS))
				.toArray(byte[][]::new);
		List<byte[]> fragments = packets(pmt(1, PCR_PID, streams), PMT_PID, 0, 173);
		assertThat(fragments.size()).isGreaterThanOrEqualTo(4);
		List<byte[]> fixture = new ArrayList<>();
		fixture.add(pat(false));
		for (byte[] fragment : fragments) {
			fixture.add(fragment);
			fixture.add(packet(0x1fff, 7, false, bytes(0xff), true));
		}
		M2tsInfo info = parse(fixture);
		assertThat(info.getStreams()).extracting(M2tsStreamInfo::getPid)
				.containsExactlyElementsOf(IntStream.range(0x1100, 0x111f).boxed().toList());
		for (int pid = 0x1100; pid < 0x111f; pid++) {
			assertAudio(info, pid);
		}
	}

	@Test
	void incompleteSectionAtEofAndOrphanContinuationNeverPublishStreams() throws IOException {
		byte[] section = smallPmt();
		for (int end = 1; end < section.length; end++) {
			assertEmpty(
					parse(List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, end)), false))));
		}
		assertEmpty(parse(List.of(pat(false), packet(PMT_PID, 0, false, section, false))));
		byte[][] streams = IntStream.range(0, 31).mapToObj(i -> es(0x81, 0x1100 + i, AC3_DESCRIPTORS))
				.toArray(byte[][]::new);
		List<byte[]> partial = packets(pmt(1, PCR_PID, streams), PMT_PID, 0, 183);
		partial.remove(partial.size() - 1);
		partial.add(0, pat(false));
		assertEmpty(parse(partial));
	}

	@Test
	void continuityGapDiscardsPartialSectionAndValidRepeatRecovers() throws IOException {
		byte[] section = smallPmt();
		List<byte[]> fixture = new ArrayList<>(
				List.of(pat(false), packet(PMT_PID, 3, true, start(Arrays.copyOf(section, 25)), false),
						packet(PMT_PID, 5, false, Arrays.copyOfRange(section, 25, section.length), false)));
		assertEmpty(parse(fixture));
		fixture.add(packet(PMT_PID, 6, true, start(section), false));
		assertAudio(parse(fixture), AUDIO_PID);
	}

	@Test
	void exactDuplicateContinuationIsIgnoredRatherThanAppended() throws IOException {
		byte[] section = smallPmt();
		byte[] middle = packet(PMT_PID, 1, false, Arrays.copyOfRange(section, 10, 25), false);
		M2tsInfo info = parse(List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, 10)), false),
				middle, middle.clone(),
				packet(PMT_PID, 2, false, Arrays.copyOfRange(section, 25, section.length), false)));
		assertThat(info.getStreams()).hasSize(1);
		assertAudio(info, AUDIO_PID);
	}

	@Test
	void continuityCounterWrapsFromFifteenToZero() throws IOException {
		byte[] section = smallPmt();
		assertAudio(parse(List.of(pat(false), packet(PMT_PID, 15, true, start(Arrays.copyOf(section, 20)), false),
				packet(PMT_PID, 0, false, Arrays.copyOfRange(section, 20, section.length), false))), AUDIO_PID);
	}

	@Test
	void sameCounterWithDifferentPayloadInvalidatesAssemblyUntilFreshStart() throws IOException {
		byte[] section = smallPmt();
		List<byte[]> fixture = new ArrayList<>(
				List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, 10)), false),
						packet(PMT_PID, 1, false, Arrays.copyOfRange(section, 10, 25), false),
						packet(PMT_PID, 1, false, bytes(0xaa, 0xbb), false),
						packet(PMT_PID, 2, false, Arrays.copyOfRange(section, 25, section.length), false)));
		assertEmpty(parse(fixture));
		fixture.add(packet(PMT_PID, 3, true, start(section), true));
		assertAudio(parse(fixture), AUDIO_PID);
	}

	@Test
	void pusiPointerPrefixFinishesPendingSectionBeforeNextSectionStarts() throws IOException {
		byte[] section = smallPmt();
		int split = section.length - 3;
		byte[] tail = Arrays.copyOfRange(section, split, section.length);
		byte[] next = pmt(1, PCR_PID, es(0x90, 0x12b2, new byte[0]));
		M2tsInfo info = parse(List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, split)), false),
				packet(PMT_PID, 1, true, concat(bytes(tail.length), tail, next), false)));
		assertThat(info.getStreams()).extracting(M2tsStreamInfo::getPid).containsExactly(AUDIO_PID);
		assertAudio(info, AUDIO_PID);
	}

	@Test
	void invalidPointersAndSectionLengthsAreRejectedAndValidRepeatRecovers() throws IOException {
		List<byte[]> invalidPayloads = new ArrayList<>();
		invalidPayloads.add(bytes(255, 0x02));
		invalidPayloads.add(bytes(4, 0xff));
		for (int length : new int[] { 0, 12, 1022, 4095 }) {
			byte[] invalid = smallPmt();
			invalid[1] = (byte) (0xb0 | (length >>> 8));
			invalid[2] = (byte) length;
			invalidPayloads.add(start(invalid));
		}
		for (byte[] payload : invalidPayloads) {
			List<byte[]> fixture = new ArrayList<>(List.of(pat(false), packet(PMT_PID, 0, true, payload, false)));
			assertEmpty(parse(fixture));
			fixture.add(packet(PMT_PID, 1, true, start(smallPmt()), false));
			assertAudio(parse(fixture), AUDIO_PID);
		}
	}

	@Test
	void malformedEsAndDescriptorLengthsRejectWholeSectionAndPermitValidRepeat() throws IOException {
		byte[] goodEs = es(0x81, AUDIO_PID, AC3_DESCRIPTORS);
		List<byte[]> malformed = new ArrayList<>();
		// A complete first stream must not leak out when a later ES is malformed.
		malformed.add(pmt(1, PCR_PID, goodEs, bytes(0x90, 0xf2, 0xb2)));
		malformed.add(pmt(1, PCR_PID, goodEs, bytes(0x90, 0xf2, 0xb2, 0xf0, 10)));
		malformed.add(pmt(1, PCR_PID, goodEs, es(0x81, 0x1108, bytes(0x05))));
		malformed.add(pmt(1, PCR_PID, goodEs, es(0x81, 0x1108, bytes(0x05, 4, 'A'))));
		byte[] badProgramLength = smallPmt();
		badProgramLength[10] = (byte) 0xff;
		badProgramLength[11] = (byte) 0xff;
		writeCrc(badProgramLength);
		malformed.add(badProgramLength);
		for (byte[] section : malformed) {
			List<byte[]> fixture = new ArrayList<>(List.of(pat(false), packet(PMT_PID, 0, true, start(section), true)));
			assertEmpty(parse(fixture));
			fixture.add(packet(PMT_PID, 1, true, start(smallPmt()), false));
			M2tsInfo recovered = parse(fixture);
			assertThat(recovered.getStreams()).hasSize(1);
			assertAudio(recovered, AUDIO_PID);
		}
	}

	@Test
	void interleavedPmtPidsMaintainIndependentAssemblyAndContinuityState() throws IOException {
		byte[] first = smallPmt();
		byte[] second = pmt(2, 0x1002, es(0x90, 0x12b2, new byte[0]));
		M2tsInfo info = parse(List.of(pat(true), packet(PMT_PID, 14, true, start(Arrays.copyOf(first, 17)), false),
				packet(0x101, 5, true, start(Arrays.copyOf(second, 2)), false),
				packet(PMT_PID, 15, false, Arrays.copyOfRange(first, 17, first.length), false),
				packet(0x101, 6, false, Arrays.copyOfRange(second, 2, second.length), false)));
		assertThat(info.getProgramPidMap()).isEqualTo(Map.of(1, PMT_PID, 2, 0x101));
		assertThat(info.getPmtPid()).isEqualTo(PMT_PID);
		assertThat(info.getPcrPid()).isEqualTo(PCR_PID);
		assertThat(info.getStreams()).extracting(M2tsStreamInfo::getPid).containsExactly(AUDIO_PID, 0x12b2);
		assertAudio(info, AUDIO_PID);
	}

	@Test
	void damagedTransportPacketsDiscardPartialSectionsAndFreshStartRecovers() throws IOException {
		byte[] section = smallPmt();
		for (int damage = 0; damage < 5; damage++) {
			byte[] middle = packet(PMT_PID, 1, false, Arrays.copyOfRange(section, 10, 25), false);
			switch (damage) {
			case 0 -> middle[5] |= (byte) 0x80; // transport_error_indicator
			case 1 -> middle[7] |= (byte) 0x80; // scrambled payload
			case 2 -> middle[8] = (byte) 255; // adaptation field overruns packet
			case 3 -> middle[9] |= (byte) 0x80; // discontinuity_indicator
			case 4 -> middle[7] &= (byte) 0xCF; // reserved adaptation_field_control
			default -> throw new AssertionError(damage);
			}
			List<byte[]> fixture = new ArrayList<>(
					List.of(pat(false), packet(PMT_PID, 0, true, start(Arrays.copyOf(section, 10)), false), middle,
							packet(PMT_PID, 2, false, Arrays.copyOfRange(section, 25, section.length), false)));
			assertEmpty(parse(fixture));
			fixture.add(packet(PMT_PID, 3, true, start(section), false));
			assertAudio(parse(fixture), AUDIO_PID);
		}
	}

	@Test
	void adaptationOnlyPacketsDoNotAdvanceContinuityButCanSignalDiscontinuity() throws IOException {
		byte[] section = smallPmt();
		for (boolean discontinuity : new boolean[] { false, true }) {
			byte[] adaptationOnly = new byte[192];
			adaptationOnly[4] = 0x47;
			adaptationOnly[5] = 1;
			adaptationOnly[7] = 0x20;
			adaptationOnly[8] = (byte) 183;
			adaptationOnly[9] = (byte) (discontinuity ? 0x80 : 0);
			List<byte[]> fixture = new ArrayList<>(List.of(pat(false),
					packet(PMT_PID, 0, true, start(Arrays.copyOf(section, 10)), false), adaptationOnly,
					packet(PMT_PID, 1, false, Arrays.copyOfRange(section, 10, section.length), false)));
			if (discontinuity) {
				assertEmpty(parse(fixture));
				byte[] fresh = packet(PMT_PID, 8, true, start(section), false);
				fresh[9] |= (byte) 0x80;
				fixture.add(fresh);
			}
			assertAudio(parse(fixture), AUDIO_PID);
		}
	}

	@Test
	void multipleSectionsAndNonzeroPointerWithoutPendingSectionAreHandled() throws IOException {
		byte[] otherTable = smallPmt();
		otherTable[0] = 3;
		assertAudio(
				parse(List.of(pat(false), packet(PMT_PID, 0, true, concat(bytes(0), otherTable, smallPmt()), true))),
				AUDIO_PID);
		assertAudio(parse(
				List.of(pat(false), packet(PMT_PID, 0, true, concat(bytes(3, 0xff, 0xff, 0xff), smallPmt()), true))),
				AUDIO_PID);
	}

	@Test
	void optionalRealSampleContainsAllStreamsAndCrossPacketRegistration() throws IOException {
		String sample = System.getProperty("brts.pmtSample");
		assumeTrue(sample != null && !sample.isBlank(), "Set brts.pmtSample to enable real-sample verification");
		M2tsInfo info = new M2tsParser().parse(Path.of(sample));
		assertThat(info.getStreams()).hasSize(31);
		assertThat(info.getPmtPid()).isEqualTo(PMT_PID);
		assertThat(info.getPcrPid()).isEqualTo(PCR_PID);
		assertThat(info.getStreams()).filteredOn(stream -> stream.getPid() == AUDIO_PID).singleElement()
				.extracting(M2tsStreamInfo::getRegistration).isEqualTo("AC-3");
		assertThat(info.getStreams()).filteredOn(stream -> stream.getPid() == 0x12b2).singleElement()
				.extracting(M2tsStreamInfo::getCodingType).isEqualTo(StreamCodingType.PRESENTATION_GRAPHICS);
	}

	private M2tsInfo parse(List<byte[]> packets) throws IOException {
		Path path = tempDir.resolve("fixture-" + fixtureNumber++ + ".m2ts");
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		for (byte[] packet : packets) {
			assertThat(packet).hasSize(192);
			content.writeBytes(packet);
		}
		Files.write(path, content.toByteArray());
		return new M2tsParser().parse(path);
	}

	private static void assertEmpty(M2tsInfo info) {
		assertThat(info.getStreams()).isEmpty();
		assertThat(info.getPcrPid()).isEqualTo(-1);
	}

	private static void assertAudio(M2tsInfo info, int pid) {
		assertThat(info.getStreams()).filteredOn(stream -> stream.getPid() == pid).singleElement().satisfies(stream -> {
			assertThat(stream.getStreamTypeByte()).isEqualTo(0x81);
			assertThat(stream.getCodingType()).isEqualTo(StreamCodingType.DOLBY_AC3);
			assertThat(stream.getRegistration()).isEqualTo("AC-3");
			assertThat(stream.getLanguage()).isEqualTo("eng");
			assertThat(stream.getSampleRateHz()).isEqualTo(48000);
			assertThat(stream.getBitrateKbps()).isEqualTo(640);
			assertThat(stream.getChannels()).isEqualTo(5);
		});
	}

	private static byte[] smallPmt() {
		return pmt(1, PCR_PID, es(0x81, AUDIO_PID, AC3_DESCRIPTORS));
	}

	private static byte[] pat(boolean twoPrograms) {
		byte[] entries = twoPrograms ? bytes(0, 1, 0xe1, 0, 0, 2, 0xe1, 1) : bytes(0, 1, 0xe1, 0);
		byte[] section = section(concat(bytes(0, 0xb0, 0, 0, 1, 0xc1, 0, 0), entries));
		return packet(0, 0, true, start(section), false);
	}

	private static byte[] pmt(int program, int pcrPid, byte[]... streams) {
		return section(
				concat(bytes(2, 0xb0, 0, program >>> 8, program, 0xc1, 0, 0, 0xe0 | (pcrPid >>> 8), pcrPid, 0xf0, 0),
						concat(streams)));
	}

	private static byte[] es(int type, int pid, byte[] descriptors) {
		return concat(bytes(type, 0xe0 | (pid >>> 8), pid, 0xf0 | (descriptors.length >>> 8), descriptors.length),
				descriptors);
	}

	/** Adds the section_length and a real MPEG-2 CRC, including for intentionally malformed ES bodies. */
	private static byte[] section(byte[] body) {
		byte[] result = Arrays.copyOf(body, body.length + 4);
		int length = result.length - 3;
		if (length > 1021) {
			throw new IllegalArgumentException("Fixture section exceeds MPEG-2 PSI limit");
		}
		result[1] = (byte) (0xb0 | (length >>> 8));
		result[2] = (byte) length;
		writeCrc(result);
		return result;
	}

	private static void writeCrc(byte[] section) {
		int crc = 0xffffffff;
		for (int i = 0; i < section.length - 4; i++) {
			crc ^= (section[i] & 0xff) << 24;
			for (int bit = 0; bit < 8; bit++) {
				crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
			}
		}
		for (int i = 0; i < 4; i++) {
			section[section.length - 4 + i] = (byte) (crc >>> (24 - 8 * i));
		}
	}

	private static List<byte[]> packets(byte[] section, int pid, int firstCc, int chunkSize) {
		List<byte[]> result = new ArrayList<>();
		for (int offset = 0; offset < section.length;) {
			boolean first = offset == 0;
			int end = Math.min(section.length, offset + Math.min(chunkSize, first ? 183 : 184));
			byte[] fragment = Arrays.copyOfRange(section, offset, end);
			result.add(packet(pid, (firstCc + result.size()) & 15, first, first ? start(fragment) : fragment, false));
			offset = end;
		}
		return result;
	}

	/** Exact payload uses adaptation stuffing; payload-only mode deliberately leaves trailing 0xff bytes. */
	private static byte[] packet(int pid, int cc, boolean pusi, byte[] payload, boolean payloadOnly) {
		if (payload.length < 1 || payload.length > 184) {
			throw new IllegalArgumentException("Fixture payload must contain 1..184 bytes");
		}
		byte[] result = new byte[192];
		Arrays.fill(result, (byte) 0xff);
		Arrays.fill(result, 0, 4, (byte) 0);
		result[4] = 0x47;
		result[5] = (byte) ((pusi ? 0x40 : 0) | (pid >>> 8));
		result[6] = (byte) pid;
		int payloadOffset = 8;
		if (payloadOnly || payload.length == 184) {
			result[7] = (byte) (0x10 | (cc & 15));
		} else {
			result[7] = (byte) (0x30 | (cc & 15));
			int adaptationLength = 183 - payload.length;
			result[8] = (byte) adaptationLength;
			if (adaptationLength > 0) {
				result[9] = 0; // No discontinuity or PCR flags in stuffing.
			}
			payloadOffset = 9 + adaptationLength;
		}
		System.arraycopy(payload, 0, result, payloadOffset, payload.length);
		return result;
	}

	private static byte[] start(byte[] section) {
		return concat(bytes(0), section);
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			out.writeBytes(part);
		}
		return out.toByteArray();
	}

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}
}
