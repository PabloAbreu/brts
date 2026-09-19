package org.brts.common.io;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/io/BufferedBinaryWriter.java' is part of BRTS.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A {@link BinaryWriter} backed by an internal {@link ByteArrayOutputStream}.
 * <p>
 * Eliminates the boilerplate of managing paired {@code ByteArrayOutputStream} / {@code BinaryWriter} instances and
 * exposes length-prefixed write helpers to mutualize the common pattern of writing an inner buffer's byte count
 * followed by its content into an outer writer.
 * <p>
 * No {@code close()} call is needed: {@link ByteArrayOutputStream} holds no I/O resources.
 */
public class BufferedBinaryWriter extends BinaryWriter {

	private final ByteArrayOutputStream buf;

	public BufferedBinaryWriter() {
		this(new ByteArrayOutputStream());
	}

	private BufferedBinaryWriter(ByteArrayOutputStream buf) {
		super(buf);
		this.buf = buf;
	}

	/** Returns all bytes written so far. */
	public byte[] toByteArray() {
		return buf.toByteArray();
	}

	/** Returns the number of bytes written so far. */
	public int byteCount() {
		return buf.size();
	}

	// -------------------------------------------------------------------------
	// Length-prefixed helpers
	// -------------------------------------------------------------------------

	/**
	 * Writes content into a temporary inner buffer, then writes a 1-byte length prefix followed by those bytes into
	 * this writer.
	 */
	public void writeBytePrefixed(ContentWriter content) throws IOException {
		byte[] bytes = capture(content);
		writeByte(bytes.length);
		writeBytes(bytes);
	}

	/**
	 * Writes content into a temporary inner buffer, then writes a 2-byte (big-endian) length prefix followed by those
	 * bytes into this writer.
	 */
	public void writeShortPrefixed(ContentWriter content) throws IOException {
		byte[] bytes = capture(content);
		writeShort(bytes.length);
		writeBytes(bytes);
	}

	/**
	 * Writes content into a temporary inner buffer, then writes a 4-byte (big-endian) length prefix followed by those
	 * bytes into this writer.
	 */
	public void writeIntPrefixed(ContentWriter content) throws IOException {
		byte[] bytes = capture(content);
		writeInt(bytes.length);
		writeBytes(bytes);
	}

	// -------------------------------------------------------------------------

	private static byte[] capture(ContentWriter content) throws IOException {
		BufferedBinaryWriter inner = new BufferedBinaryWriter();
		content.write(inner);
		return inner.toByteArray();
	}

	/**
	 * No-op: {@link ByteArrayOutputStream} holds no I/O resources and does not need to be closed.
	 */
	@Override
	public void close() {
		// intentional no-op
	}

	// -------------------------------------------------------------------------

	/**
	 * Functional interface for writing content into a {@link BufferedBinaryWriter}.
	 */
	@FunctionalInterface
	public interface ContentWriter {
		void write(BufferedBinaryWriter w) throws IOException;
	}
}
