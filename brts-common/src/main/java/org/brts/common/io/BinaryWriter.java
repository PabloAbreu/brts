package org.brts.common.io;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/io/BinaryWriter.java' is part of BRTS.
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Writes big-endian binary data to an OutputStream. All Blu-ray binary structures use big-endian byte ordering.
 */
@RequiredArgsConstructor
public class BinaryWriter implements AutoCloseable {
	private final OutputStream out;

	/** the number of bytes written so far. */
	private @Getter long position = 0;

	public void writeByte(int value) throws IOException {
		out.write(value & 0xFF);
		position++;
	}

	public void writeShort(int value) throws IOException {
		writeByte((value >> 8) & 0xFF);
		writeByte(value & 0xFF);
	}

	public void writeInt(long value) throws IOException {
		writeShort((int) ((value >> 16) & 0xFFFF));
		writeShort((int) (value & 0xFFFF));
	}

	public void writeLong(long value) throws IOException {
		writeInt((value >> 32) & 0xFFFFFFFFL);
		writeInt(value & 0xFFFFFFFFL);
	}

	public void writeBytes(byte[] data) throws IOException {
		out.write(data);
		position += data.length;
	}

	public void writeAscii(String s) throws IOException {
		writeBytes(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
	}

	/** Writes {@code count} zero bytes (padding). */
	public void writePadding(int count) throws IOException {
		out.write(new byte[count]);
		position += count;
	}

	/** Writes {@code count} bytes (padding with a particular byte). */
	public void writePadding(int count, int fill) throws IOException {
		byte[] b = new byte[count];
		Arrays.fill(b, (byte) fill);
		out.write(b);
		position += count;
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	public void padToFour() throws IOException {
		// pad result to 4-byte boundary if needed (not sure if this is actually
		// required,
		// but seems to be the pattern in real files)
		if (getPosition() % 4 != 0) {
			writePadding(4 - (int) (getPosition() % 4));
		}
	}
}
