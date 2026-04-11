package org.brts.common.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes big-endian binary data to an OutputStream. All Blu-ray binary structures use
 * big-endian byte ordering.
 */
public class BinaryWriter implements AutoCloseable {

	private final OutputStream out;

	private long position = 0;

	public BinaryWriter(OutputStream out) {
		this.out = out;
	}

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

	/** Returns the number of bytes written so far. */
	public long getPosition() {
		return position;
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

}
