package org.brts.common.io;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/io/BinaryReader.java' is part of BRTS.
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
import java.io.InputStream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Reads big-endian binary data from an InputStream. All Blu-ray binary structures use big-endian byte ordering.
 */
@RequiredArgsConstructor
public class BinaryReader implements AutoCloseable {

	private final InputStream in;

	/** The number of bytes read so far. */
	private @Getter long position = 0;

	public int readUnsignedByte() throws IOException {
		int b = in.read();
		if (b < 0)
			throw new java.io.EOFException("Unexpected end of stream at position " + position);
		position++;
		return b;
	}

	public int readUnsignedShort() throws IOException {
		return (readUnsignedByte() << 8) | readUnsignedByte();
	}

	public long readUnsignedInt() throws IOException {
		return ((long) readUnsignedShort() << 16) | readUnsignedShort();
	}

	public long readUnsignedLong() throws IOException {
		return (readUnsignedInt() << 32) | readUnsignedInt();
	}

	public byte[] readBytes(int length) throws IOException {
		byte[] buf = new byte[length];
		int read = 0;
		while (read < length) {
			int n = in.read(buf, read, length - read);
			if (n < 0)
				throw new java.io.EOFException("Unexpected end of stream at position " + position);
			read += n;
			position += n;
		}
		return buf;
	}

	public String readAscii(int length) throws IOException {
		return new String(readBytes(length), java.nio.charset.StandardCharsets.US_ASCII);
	}

	public void skip(long n) throws IOException {
		long skipped = in.skip(n);
		position += skipped;
		// skip() may return less than requested; loop if needed
		long remaining = n - skipped;
		while (remaining > 0) {
			skipped = in.skip(remaining);
			if (skipped <= 0)
				throw new java.io.EOFException("Unexpected end of stream at position " + position);
			position += skipped;
			remaining -= skipped;
		}
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
