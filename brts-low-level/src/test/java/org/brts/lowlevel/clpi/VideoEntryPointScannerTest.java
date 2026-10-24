/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/VideoEntryPointScannerTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.brts.lowlevel.clpi.M2tsEpFixtures.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoEntryPointScannerTest {

	private record Entry(long pts, long spn) {
	}

	private final List<Entry> entries = new ArrayList<>();

	private final List<Long> timestamps = new ArrayList<>();

	private VideoEntryPointScanner scanner(StreamCodingType codec) {
		return new VideoEntryPointScanner(codec, pts -> {
			timestamps.add(pts);
			return pts;
		}, (pts, spn) -> entries.add(new Entry(pts, spn)));
	}

	private void feed(VideoEntryPointScanner scanner, byte[] data, long spn) {
		scanner.accept(data, 0, data.length, true, spn);
	}

	@ParameterizedTest
	@ValueSource(ints = { 16, 17, 18, 19, 20, 21 })
	void acceptsDefinedHevcIrapTypesAtEveryFragmentBoundary(int type) {
		byte[] payload = pes(90_000, hevc(type));
		for (int split = 1; split < payload.length; split++) {
			entries.clear();
			VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
			scanner.accept(payload, 0, split, true, 42);
			scanner.accept(payload, split, payload.length - split, false, 43);
			assertThat(entries).as("split at byte %s", split).containsExactly(new Entry(90_000, 42));
		}
	}

	@Test
	void scansPastLargeMetadataWithoutBufferingTheAccessUnit() {
		byte[] sei = new byte[100_000];
		Arrays.fill(sei, (byte) 0x55);
		byte[] payload = pes(123_456, join(hevc(35), hevc(32), hevc(33), hevc(34), hevc(39), sei, hevc(21)));
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		for (int i = 0; i < payload.length; i++)
			scanner.accept(payload, i, 1, i == 0, 10 + i);
		assertThat(entries).containsExactly(new Entry(123_456, 10));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 8, 9, 15, 22, 23, 31, 32, 33, 34, 35, 39, 40, 62 })
	void rejectsNonRandomAccessHevcNalTypes(int type) {
		feed(scanner(StreamCodingType.H265_HEVC), pes(42, hevc(type)), 1);
		assertThat(entries).isEmpty();
	}

	@Test
	void ignoresEnhancementLayerAndRejectsInvalidHevcHeaders() {
		for (byte[] nal : List.of(bytes(0, 0, 1, 0xA6, 1, 0x80), // forbidden bit
				bytes(0, 0, 1, 38, 0, 0x80), // temporal_id_plus1 == 0
				bytes(0, 0, 1, 38, 9, 0x80))) { // layer 1
			feed(scanner(StreamCodingType.H265_HEVC), pes(42, nal), 1);
		}
		assertThat(entries).isEmpty();
	}

	@Test
	void doesNotDuplicateSlicesOrInventPtsForLaterPicturesInTheSamePes() {
		byte[] laterSlice = bytes(0, 0, 1, 38, 1, 0x40);
		feed(scanner(StreamCodingType.H265_HEVC), pes(42, join(hevc(19), laterSlice, hevc(19))), 10);
		assertThat(entries).containsExactly(new Entry(42, 10));
	}

	@Test
	void consumesTimestampOfNonKeyframeBeforeLaterIrap() {
		feed(scanner(StreamCodingType.H265_HEVC), pes(42, join(hevc(1), hevc(21))), 10);
		assertThat(entries).isEmpty();
	}

	@Test
	void trailingZerosOfPreviousPictureCannotStealTheNextPesTimestamp() {
		for (int zeroCount = 1; zeroCount <= 8; zeroCount++) {
			entries.clear();
			VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
			feed(scanner, pes(42, join(hevc(19), new byte[zeroCount])), 10);
			feed(scanner, pes(100, bytes(0, 0, 1, 42, 1, 0x80)), 20);
			assertThat(entries).as("trailing zeros: %s", zeroCount).containsExactly(new Entry(42, 10),
					new Entry(100, 20));
		}
	}

	@Test
	void preservesNalAndOwningPesAcrossNoPtsPesContinuation() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		feed(scanner, pes(42, bytes(0, 0)), 10);
		feed(scanner, pes(-1, bytes(1, 38, 1, 0x80)), 20);
		assertThat(entries).containsExactly(new Entry(42, 10));
	}

	@Test
	void metadataCanSpanPesWithoutLosingTheInitialAnchor() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		feed(scanner, pes(42, join(hevc(35), hevc(39))), 10);
		feed(scanner, pes(-1, join(bytes(0x55, 0x55), hevc(21))), 20);
		assertThat(entries).containsExactly(new Entry(42, 10));
	}

	@Test
	void newPictureWithoutPtsDoesNotReusePreviousPesTimestamp() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		feed(scanner, pes(42, hevc(19)), 10);
		feed(scanner, pes(-1, hevc(21)), 20);
		feed(scanner, pes(100, hevc(21)), 30);
		assertThat(entries).containsExactly(new Entry(42, 10), new Entry(100, 30));
	}

	@Test
	void rejectsMalformedPesHeadersAndResynchronizes() {
		for (int index : new int[] { 0, 2, 3, 6, 7, 8, 9, 11, 13 }) {
			entries.clear();
			timestamps.clear();
			VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
			byte[] malformed = pes(42, hevc(21));
			malformed[index] = 0;
			if (index == 0)
				malformed[index] = 1;
			if (index == 7)
				malformed[index] = 0x40; // forbidden PTS_DTS_flags
			feed(scanner, malformed, 10);
			assertThat(entries).as("corrupt PES byte %s", index).isEmpty();
			assertThat(timestamps).isEmpty();
			feed(scanner, pes(100, hevc(19)), 20);
			assertThat(entries).containsExactly(new Entry(100, 20));
		}
	}

	@Test
	void allowsPesPriorityAndOtherUnrestrictedFixedHeaderFlags() {
		byte[] payload = pes(42, hevc(21));
		payload[6] = (byte) 0x8F; // priority, alignment, copyright and original flags are all legal.
		feed(scanner(StreamCodingType.H265_HEVC), payload, 10);
		assertThat(entries).containsExactly(new Entry(42, 10));
	}

	@Test
	void validatesDtsAndSupportsMaximumOptionalHeaderLength() {
		byte[] optional = new byte[255];
		Arrays.fill(optional, (byte) 0xFF);
		System.arraycopy(timestamp(42, 3), 0, optional, 0, 5);
		System.arraycopy(timestamp(40, 1), 0, optional, 5, 5);
		byte[] payload = join(bytes(0, 0, 1, 0xE0, 0, 0, 0x80, 0xC0, 255), optional, hevc(21));
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		for (int i = 0; i < payload.length; i++)
			scanner.accept(payload, i, 1, i == 0, i);
		assertThat(entries).containsExactly(new Entry(42, 0));
		entries.clear();
		payload[14] &= (byte) 0xFE;
		feed(scanner(StreamCodingType.H265_HEVC), payload, 1);
		assertThat(entries).isEmpty();
	}

	@Test
	void honorsDeclaredPesLengthAndRejectsShortLengths() {
		byte[] payload = pes(42, hevc(21));
		payload[5] = 8; // only header, no elementary payload
		feed(scanner(StreamCodingType.H265_HEVC), payload, 1);
		assertThat(entries).isEmpty();
		payload[5] = 7; // even optional header cannot fit
		feed(scanner(StreamCodingType.H265_HEVC), payload, 1);
		assertThat(entries).isEmpty();
		payload[5] = (byte) (payload.length - 6);
		feed(scanner(StreamCodingType.H265_HEVC), payload, 1);
		assertThat(entries).containsExactly(new Entry(42, 1));
	}

	@Test
	void resetAndIncompleteEofCannotCreateEntries() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H265_HEVC);
		feed(scanner, pes(42, bytes(0, 0, 1, 38)), 1);
		assertThat(entries).isEmpty();
		scanner.reset();
		scanner.accept(bytes(1, 0x80), 0, 2, false, 2);
		assertThat(entries).isEmpty();
		feed(scanner, pes(100, hevc(21)), 3);
		assertThat(entries).containsExactly(new Entry(100, 3));
	}

	@Test
	void retainsAvcIdrSemanticsAndSkipsSeiWithThreeByteStartCodes() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.H264_AVC);
		byte[] payload = pes(42, join(avc(9), avc(7), avc(8), avc(6), avc(5), bytes(0, 0, 1, 0x65, 0x40)));
		for (int i = 0; i < payload.length; i++)
			scanner.accept(payload, i, 1, i == 0, i);
		feed(scanner, pes(100, avc(1)), 100);
		feed(scanner, pes(200, avc(7)), 200);
		assertThat(entries).containsExactly(new Entry(42, 0));
	}

	@Test
	void recognizesMpeg2IPicturesAcrossSingleByteFragments() {
		VideoEntryPointScanner scanner = scanner(StreamCodingType.MPEG2_VIDEO);
		byte[] payload = pes(42, join(bytes(0, 0, 1, 0xB3, 0x55, 0x55), mpeg(1)));
		for (int i = 0; i < payload.length; i++)
			scanner.accept(payload, i, 1, i == 0, i);
		feed(scanner, pes(100, mpeg(2)), 100);
		feed(scanner, pes(200, mpeg(3)), 200);
		assertThat(entries).containsExactly(new Entry(42, 0));
	}

}
