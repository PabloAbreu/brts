/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/BitrateCodeTest.java' is part of BRTS.
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

class BitrateCodeTest {

	private static final int[] BITRATES_KBPS = { 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384,
			448, 512, 576, 640 };

	@Test
	void mapsEveryAtscCodeToItsExactBitrateAndBack() {
		for (int code = 0; code < BITRATES_KBPS.length; code++) {
			BitrateCode bitrate = BitrateCode.fromCode(code);

			assertThat(bitrate).isNotNull();
			assertThat(bitrate.getCode()).isEqualTo(code);
			assertThat(bitrate.getBitrateKbps()).isEqualTo(BITRATES_KBPS[code]);
			assertThat(BitrateCode.fromBitrate(BITRATES_KBPS[code])).isSameAs(bitrate);
		}
	}

	@Test
	void rejectsReservedAndUpperBoundCodes() {
		for (int code = 19; code <= 63; code++)
			assertThat(BitrateCode.fromCode(code)).isNull();
	}

	@Test
	void rejectsUnsupportedExactBitrates() {
		assertThat(BitrateCode.fromBitrate(0)).isNull();
		assertThat(BitrateCode.fromBitrate(96_000)).isNull();
		assertThat(BitrateCode.fromBitrate(384_000)).isNull();
	}
}
