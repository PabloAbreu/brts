package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/m2ts/M2tsIgsMuxerTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.lowlevel.m2ts.M2tsIgsMuxer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsIgsMuxerTest {

	@TempDir
	Path tempDir;

	@Test
	void mux_rejectsSegmentWhosePesPacketLengthExceedsUnsignedShort() throws Exception {
		byte[] igs = new byte[3 + 0xFFFF];
		igs[0] = 0x15;
		igs[1] = (byte) 0xFF;
		igs[2] = (byte) 0xFF;
		Path input = tempDir.resolve("oversized.igs");
		Files.write(input, igs);

		assertThatThrownBy(() -> new M2tsIgsMuxer().mux(input, tempDir.resolve("output.m2ts")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("PES packet length 65551 exceeds maximum 65535 for OBJECT_DEFINITION segment");
	}

}
