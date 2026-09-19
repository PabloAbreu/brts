package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/BitReader.java' is part of BRTS.
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

/**
 * MSB-first bit reader for H.264 RBSP bytes (emulation-prevention bytes already removed by the caller).
 */
public final class BitReader {

	private final byte[] buf;

	private int pos; // absolute bit position (MSB = 0 within each byte)

	BitReader(byte[] rbsp) {
		this.buf = rbsp;
	}

	boolean canRead() {
		return (pos >> 3) < buf.length;
	}

	int readBit() {
		int byteIdx = pos >> 3;
		int shift = 7 - (pos & 7);
		pos++;
		return byteIdx < buf.length ? (buf[byteIdx] >> shift) & 1 : 0;
	}

	int readBits(int n) {
		int v = 0;
		for (int i = 0; i < n; i++)
			v = (v << 1) | readBit();
		return v;
	}

	/** Unsigned Exp-Golomb code (H.264 spec §9.1). */
	int readUE() {
		int zeros = 0;
		while (canRead() && readBit() == 0)
			zeros++;
		return zeros == 0 ? 0 : (1 << zeros) - 1 + readBits(zeros);
	}

	/** Signed Exp-Golomb code mapped from unsigned. */
	int readSE() {
		int ue = readUE();
		return (ue & 1) != 0 ? (ue + 1) >> 1 : -(ue >> 1);
	}

}
