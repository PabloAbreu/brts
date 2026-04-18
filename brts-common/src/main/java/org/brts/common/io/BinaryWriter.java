package org.brts.common.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

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

	/** Writes {@code count} bytes (padding with a particular byte). */
	public void writePadding(int count, int fill) throws IOException {
		byte[] b = new byte[count];
		Arrays.fill(b, (byte) fill);
		out.write(b);
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

	public void padToFour() throws IOException {
		// pad result to 4-byte boundary if needed (not sure if this is actually
		// required,
		// but seems to be the pattern in real files)
		if (getPosition() % 4 != 0) {
			writePadding(4 - (int) (getPosition() % 4));
		}
	}

}
