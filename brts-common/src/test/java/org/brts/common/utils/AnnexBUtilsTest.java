package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/utils/AnnexBUtilsTest.java' is part of BRTS.
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

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class AnnexBUtilsTest {

	@Test
	void recognizesThreeAndFourByteStartCodes() {
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x01, 0x65 }))).isTrue();
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x00, 0x01, 0x65 }))).isTrue();
	}

	@Test
	void respectsBufferPositionWithoutModifyingIt() {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[] { 0x7F, 0x00, 0x00, 0x00, 0x01, 0x65 });
		buffer.position(1);

		assertThat(AnnexBUtils.isAnnexBFormat(buffer)).isTrue();
		assertThat(buffer.position()).isEqualTo(1);
	}

	@Test
	void rejectsDataWithoutAnAnnexBStartCode() {
		assertThat(AnnexBUtils.isAnnexBFormat(ByteBuffer.wrap(new byte[] { 0x00, 0x00, 0x02, 0x65 }))).isFalse();
	}
}
