package org.brts.common.io;

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
