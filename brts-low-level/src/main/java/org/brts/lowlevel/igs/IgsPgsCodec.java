package org.brts.lowlevel.igs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindow;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.igs.model.PaletteEntry;

/**
 * Binary segment encoders and integer-writing helpers shared by {@link IgsMuxer} and
 * {@code org.brts.lowlevel.pgs.PgsMuxer}: PDS, ODS and WDS bodies are byte-for-byte identical between IGS and PGS, only
 * the composition segment (ICS vs PCS) and the outer segment framing differ.
 */
public final class IgsPgsCodec {

	private IgsPgsCodec() {
	}

	/** Encodes a Palette Definition Segment body. */
	public static byte[] encodePalette(IgsPalette pal) {
		// id(8) + version(8) + N * (entryId(8) + Y(8) + Cr(8) + Cb(8) + alpha(8))
		int size = 2 + pal.getEntries().size() * 5;
		byte[] d = new byte[size];
		d[0] = (byte) pal.getId();
		d[1] = (byte) pal.getVersion();
		int pos = 2;
		for (PaletteEntry e : pal.getEntries()) {
			d[pos++] = (byte) e.getEntryId();
			d[pos++] = (byte) e.getY();
			d[pos++] = (byte) e.getCr();
			d[pos++] = (byte) e.getCb();
			d[pos++] = (byte) e.getAlpha();
		}
		return d;
	}

	/** Encodes an Object Definition Segment body from an in-memory {@link IgsObject}. */
	public static byte[] encodeObject(IgsObject obj) {
		boolean firstInSeq = obj.getSequenceDescriptor() != null && obj.getSequenceDescriptor().isFirstInSequence();
		boolean lastInSeq = obj.getSequenceDescriptor() != null && obj.getSequenceDescriptor().isLastInSequence();

		byte[] rleData = obj.getRleData() != null ? obj.getRleData() : new byte[0];

		int headerSize = 4; // id(16) + version(8) + seq_desc(8)
		if (firstInSeq) {
			headerSize += 7; // dataLength(24) + width(16) + height(16)
		}

		byte[] d = new byte[headerSize + rleData.length];
		int pos = 0;

		// id (16 bits)
		d[pos++] = (byte) ((obj.getId() >> 8) & 0xFF);
		d[pos++] = (byte) (obj.getId() & 0xFF);
		// version (8 bits)
		d[pos++] = (byte) obj.getVersion();
		// sequence descriptor (8 bits)
		int seqByte = 0;
		if (firstInSeq)
			seqByte |= 0x80;
		if (lastInSeq)
			seqByte |= 0x40;
		d[pos++] = (byte) seqByte;

		if (firstInSeq) {
			int dataLength = obj.getDataLength() > 0 ? obj.getDataLength() : rleData.length + 4; // +4 = width(2) +
																									// height(2)
			// dataLength (24 bits)
			d[pos++] = (byte) ((dataLength >> 16) & 0xFF);
			d[pos++] = (byte) ((dataLength >> 8) & 0xFF);
			d[pos++] = (byte) (dataLength & 0xFF);
			// width (16 bits)
			d[pos++] = (byte) ((obj.getWidth() >> 8) & 0xFF);
			d[pos++] = (byte) (obj.getWidth() & 0xFF);
			// height (16 bits)
			d[pos++] = (byte) ((obj.getHeight() >> 8) & 0xFF);
			d[pos++] = (byte) (obj.getHeight() & 0xFF);
		}

		System.arraycopy(rleData, 0, d, pos, rleData.length);
		return d;
	}

	/** Encodes a Window Definition Segment body. */
	public static byte[] encodeWindowDef(IgsWindowDefinition wds) {
		List<IgsWindow> windows = wds.getWindows();
		byte[] d = new byte[1 + windows.size() * 9];
		d[0] = (byte) windows.size();
		int pos = 1;
		for (IgsWindow w : windows) {
			d[pos++] = (byte) w.getId();
			d[pos++] = (byte) ((w.getX() >> 8) & 0xFF);
			d[pos++] = (byte) (w.getX() & 0xFF);
			d[pos++] = (byte) ((w.getY() >> 8) & 0xFF);
			d[pos++] = (byte) (w.getY() & 0xFF);
			d[pos++] = (byte) ((w.getWidth() >> 8) & 0xFF);
			d[pos++] = (byte) (w.getWidth() & 0xFF);
			d[pos++] = (byte) ((w.getHeight() >> 8) & 0xFF);
			d[pos++] = (byte) (w.getHeight() & 0xFF);
		}
		return d;
	}

	// -------------------------------------------------------------------------
	// Integer-writing helpers
	// -------------------------------------------------------------------------

	public static void writeU16(OutputStream out, int value) throws IOException {
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	public static void writeU16(ByteArrayOutputStream out, int value) {
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	public static void writeU24(OutputStream out, int value) throws IOException {
		out.write((value >> 16) & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	public static void writeU32(OutputStream out, int value) throws IOException {
		out.write((value >> 24) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	/** Writes a 33-bit PTS: skip(7) + bit[32] as byte[0].bit0, then 4 bytes. */
	public static void writePts33(OutputStream out, long pts) throws IOException {
		int hiBit = (int) ((pts >> 32) & 1);
		out.write(hiBit & 0x01);
		out.write((int) ((pts >> 24) & 0xFF));
		out.write((int) ((pts >> 16) & 0xFF));
		out.write((int) ((pts >> 8) & 0xFF));
		out.write((int) (pts & 0xFF));
	}

}
