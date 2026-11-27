/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/PesTimestampParserTest.java' is part of BRTS.
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

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PesTimestampParserTest {

	@Test
	void decodeTimestamp_decodes33BitTimestampAtOffset() {
		byte[] field = new byte[8];
		long expected = 0x1_2345_6789L;
		System.arraycopy(encodeTimestamp(expected, 2), 0, field, 2, 5);

		assertThat(PesTimestampParser.decodeTimestamp(field, 2)).isEqualTo(expected);
		assertThat(PesTimestampParser.decodeTimestamp(field, 4)).isEqualTo(-1);
	}

	@Test
	void parsePtsAndDts_readsBothTimestampFields() {
		long pts = 0x1_2345_6789L;
		long dts = 0x0_7654_3210L;
		byte[] pes = pesHeader(3, 10);
		System.arraycopy(encodeTimestamp(pts, 3), 0, pes, 9, 5);
		System.arraycopy(encodeTimestamp(dts, 1), 0, pes, 14, 5);

		assertThat(PesTimestampParser.parsePts(pes, 0, pes.length)).isEqualTo(pts);
		assertThat(PesTimestampParser.parseDts(pes, 0, pes.length)).isEqualTo(dts);
	}

	@Test
	void parsePtsAndDts_returnsMinusOneWhenAbsentOrTruncated() {
		byte[] noTimestamp = pesHeader(0, 0);
		byte[] ptsOnly = pesHeader(2, 5);
		System.arraycopy(encodeTimestamp(1234, 2), 0, ptsOnly, 9, 5);

		assertThat(PesTimestampParser.parsePts(noTimestamp, 0, noTimestamp.length)).isEqualTo(-1);
		assertThat(PesTimestampParser.parseDts(ptsOnly, 0, ptsOnly.length)).isEqualTo(-1);
		assertThat(PesTimestampParser.parsePts(ptsOnly, 0, ptsOnly.length - 1)).isEqualTo(-1);
		assertThat(PesTimestampParser.parsePts(ptsOnly, -1, ptsOnly.length)).isEqualTo(-1);
	}

	private static byte[] pesHeader(int ptsDtsFlags, int headerDataLength) {
		byte[] pes = new byte[9 + headerDataLength];
		pes[0] = 0;
		pes[1] = 0;
		pes[2] = 1;
		pes[3] = (byte) 0xBD;
		pes[6] = (byte) 0x80;
		pes[7] = (byte) (ptsDtsFlags << 6);
		pes[8] = (byte) headerDataLength;
		return pes;
	}

	private static byte[] encodeTimestamp(long value, int prefix) {
		return new byte[] { (byte) ((prefix << 4) | (((value >> 29) & 0x0E)) | 1), (byte) (value >> 22),
				(byte) (((value >> 14) & 0xFE) | 1), (byte) (value >> 7), (byte) (((value << 1) & 0xFE) | 1) };
	}

}
