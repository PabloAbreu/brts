package org.brts.common.io;

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

}
