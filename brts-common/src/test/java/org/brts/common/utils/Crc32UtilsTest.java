package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/utils/Crc32UtilsTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Crc32UtilsTest {

	@Test
	void calculatesMpeg2Crc32() {
		byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

		assertThat(Crc32Utils.crc32(data, 0, data.length)).isEqualTo(0x0376E6E7L);
	}

	@Test
	void calculatesCrcForRequestedRange() {
		byte[] data = "prefix123456789suffix".getBytes(StandardCharsets.US_ASCII);

		assertThat(Crc32Utils.crc32(data, 6, 9)).isEqualTo(0x0376E6E7L);
	}
}
