package org.brts.common.io;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/io/ByteArrayBinaryWriter.java' is part of BRTS.
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

public class ByteArrayBinaryWriter {
	private final ByteArrayOutputStream out;
	private final BinaryWriter writer;

	public ByteArrayBinaryWriter() {
		this.out = new ByteArrayOutputStream();
		this.writer = new BinaryWriter(out);
	}

	public ByteArrayBinaryWriter(int size) {
		this.out = new ByteArrayOutputStream(size);
		this.writer = new BinaryWriter(out);
	}

	public long getPosition() {
		return writer.getPosition();
	}

	public void writeByte(int value) throws IOException {
		writer.writeByte(value);
	}

	public void writeShort(int value) throws IOException {
		writer.writeShort(value);
	}

	public void writeInt(long value) throws IOException {
		writer.writeInt(value);
	}

	public void writeLong(long value) throws IOException {
		writer.writeLong(value);
	}

	public void writeBytes(byte[] data) throws IOException {
		writer.writeBytes(data);
	}

	public void writeAscii(String s) throws IOException {
		writer.writeAscii(s);
	}

	public void writePadding(int count) throws IOException {
		writer.writePadding(count);
	}

	public void writePadding(int count, int fill) throws IOException {
		writer.writePadding(count, fill);
	}

	public void padToFour() throws IOException {
		writer.padToFour();
	}

	public int size() {
		return out.size();
	}

	public byte[] toByteArray() {
		return out.toByteArray();
	}

	public byte[] toSizePrefixedByteArray() throws IOException {
		ByteArrayBinaryWriter w = new ByteArrayBinaryWriter(Integer.BYTES + size());
		w.writeInt(size());
		w.writeBytes(toByteArray());
		return w.toByteArray();
	}

}
