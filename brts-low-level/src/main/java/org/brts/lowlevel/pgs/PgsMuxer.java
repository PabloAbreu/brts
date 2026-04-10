package org.brts.lowlevel.pgs;

import org.brts.lowlevel.igs.IgsSegmentType;
import org.brts.lowlevel.igs.model.*;
import org.brts.lowlevel.pgs.model.PgsCompositionSegment;
import org.brts.lowlevel.pgs.model.PgsDisplaySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Encodes PGS (Presentation Graphic Stream) display sets into raw elementary
 * stream bytes.
 * <p>
 * PGS shares its binary format for PDS, ODS, WDS, and END segments with IGS;
 * only the composition segment differs (PCS = 0x16 vs ICS = 0x18).
 * This muxer reuses the encoding logic from {@link org.brts.lowlevel.igs.IgsMuxer}
 * for the shared segment types and adds PCS encoding.
 *
 * <h2>Segment ordering per display set</h2>
 * <ol>
 *   <li>PCS (Presentation Composition Segment)</li>
 *   <li>WDS (Window Definition Segments)</li>
 *   <li>PDS (Palette Definition Segments)</li>
 *   <li>ODS (Object Definition Segments)</li>
 *   <li>END (End of Display)</li>
 * </ol>
 */
public class PgsMuxer {

    private static final Logger log = LoggerFactory.getLogger(PgsMuxer.class);

    /**
     * Encodes a fully populated {@link PgsDisplaySet} into raw PGS elementary
     * stream bytes.
     *
     * @param displaySet the in-memory display set to encode
     * @return raw PGS ES bytes
     * @throws IOException on encoding error
     */
    public byte[] encodeDisplaySet(PgsDisplaySet displaySet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. PCS
        if (displaySet.getCompositionSegment() != null) {
            byte[] pcsData = encodePcs(displaySet.getCompositionSegment());
            writeSegment(out, IgsSegmentType.PG_COMPOSITION, pcsData);
        }

        // 2. WDS
        if (displaySet.getWindowDefinitions() != null) {
            for (IgsWindowDefinition wds : displaySet.getWindowDefinitions()) {
                byte[] wdsData = encodeWindowDef(wds);
                writeSegment(out, IgsSegmentType.WINDOW_DEFINITION, wdsData);
            }
        }

        // 3. PDS
        if (displaySet.getPalettes() != null) {
            for (IgsPalette pal : displaySet.getPalettes()) {
                byte[] pdsData = encodePalette(pal);
                writeSegment(out, IgsSegmentType.PALETTE_DEFINITION, pdsData);
            }
        }

        // 4. ODS
        if (displaySet.getObjects() != null) {
            for (IgsObject obj : displaySet.getObjects()) {
                byte[] odsData = encodeObject(obj);
                writeSegment(out, IgsSegmentType.OBJECT_DEFINITION, odsData);
            }
        }

        // 5. END_OF_DISPLAY
        writeSegment(out, IgsSegmentType.END_OF_DISPLAY, new byte[0]);

        return out.toByteArray();
    }

    // ── PCS encoding (PGS-specific) ────────────────────────────────────────

    /**
     * Encodes a Presentation Composition Segment body.
     * <p>
     * Binary layout:
     * <pre>
     *   VideoDescriptor      : width(16) + height(16) + frameRate(4) + pad(4)  = 5 bytes
     *   CompositionDescriptor: number(16) + state(2) + pad(6)                  = 3 bytes
     *   paletteUpdateFlag    : 1 byte (0x80 = true, 0x00 = false)
     *   paletteId            : 1 byte
     *   numCompositionObjects: 1 byte
     *   For each composition object:
     *     objectId(16) + windowId(8) + flags(8) + x(16) + y(16)               = 8 bytes
     *     if cropFlag: cropX(16) + cropY(16) + cropW(16) + cropH(16)          = 8 bytes
     * </pre>
     */
    byte[] encodePcs(PgsCompositionSegment pcs) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Video descriptor
        VideoDescriptor vd = pcs.getVideoDescriptor();
        writeU16(buf, vd.getWidth());
        writeU16(buf, vd.getHeight());
        buf.write((vd.getFrameRateCode() << 4) & 0xF0);

        // Composition descriptor
        CompositionDescriptor cd = pcs.getCompositionDescriptor();
        writeU16(buf, cd.getNumber());
        buf.write((cd.getState() << 6) & 0xC0);

        // Palette update flag
        buf.write(pcs.isPaletteUpdateFlag() ? 0x80 : 0x00);

        // Palette id
        buf.write(pcs.getPaletteIdRef() & 0xFF);

        // Composition objects
        List<CompositionObject> objects = pcs.getCompositionObjects();
        buf.write(objects.size() & 0xFF);

        for (CompositionObject co : objects) {
            writeU16(buf, co.getObjectIdRef());
            buf.write(co.getWindowIdRef() & 0xFF);

            int flags = 0;
            if (co.isForcedOn()) flags |= 0x40;
            if (co.isCropFlag()) flags |= 0x80;
            buf.write(flags);

            writeU16(buf, co.getX());
            writeU16(buf, co.getY());

            if (co.isCropFlag()) {
                writeU16(buf, co.getCropX());
                writeU16(buf, co.getCropY());
                writeU16(buf, co.getCropWidth());
                writeU16(buf, co.getCropHeight());
            }
        }

        return buf.toByteArray();
    }

    // ── Shared segment encoders (identical to IGS) ──────────────────────────

    /**
     * Encodes a Palette Definition Segment body.
     */
    byte[] encodePalette(IgsPalette pal) {
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

    /**
     * Encodes an Object Definition Segment body.
     */
    byte[] encodeObject(IgsObject obj) {
        boolean firstInSeq = obj.getSequenceDescriptor() != null
                && obj.getSequenceDescriptor().isFirstInSequence();
        boolean lastInSeq = obj.getSequenceDescriptor() != null
                && obj.getSequenceDescriptor().isLastInSequence();

        byte[] rleData = obj.getRleData() != null ? obj.getRleData() : new byte[0];

        int headerSize = 4; // id(16) + version(8) + seq_desc(8)
        if (firstInSeq) {
            headerSize += 7; // dataLength(24) + width(16) + height(16)
        }

        byte[] d = new byte[headerSize + rleData.length];
        int pos = 0;

        d[pos++] = (byte) ((obj.getId() >> 8) & 0xFF);
        d[pos++] = (byte) (obj.getId() & 0xFF);
        d[pos++] = (byte) obj.getVersion();

        int seqByte = 0;
        if (firstInSeq) seqByte |= 0x80;
        if (lastInSeq)  seqByte |= 0x40;
        d[pos++] = (byte) seqByte;

        if (firstInSeq) {
            int dataLength = obj.getDataLength() > 0
                    ? obj.getDataLength()
                    : rleData.length + 4;
            d[pos++] = (byte) ((dataLength >> 16) & 0xFF);
            d[pos++] = (byte) ((dataLength >> 8) & 0xFF);
            d[pos++] = (byte) (dataLength & 0xFF);
            d[pos++] = (byte) ((obj.getWidth() >> 8) & 0xFF);
            d[pos++] = (byte) (obj.getWidth() & 0xFF);
            d[pos++] = (byte) ((obj.getHeight() >> 8) & 0xFF);
            d[pos++] = (byte) (obj.getHeight() & 0xFF);
        }

        System.arraycopy(rleData, 0, d, pos, rleData.length);
        return d;
    }

    /**
     * Encodes a Window Definition Segment body.
     */
    byte[] encodeWindowDef(IgsWindowDefinition wds) {
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

    // ── Binary helpers ──────────────────────────────────────────────────────

    /**
     * Writes a segment: 2-byte "PG" magic + PTS(32) + DTS(32) + 1-byte type
     * + 2-byte BE length + data.
     * <p>
     * Note: In raw PGS ES files each segment is prefixed with the PES-like
     * header "PG" + PTS + DTS. When PTS/DTS are not set we default to 0.
     */
    private void writeSegment(OutputStream out, IgsSegmentType type, byte[] data) throws IOException {
        // PG header (like PES encapsulation for raw .sup files)
        out.write('P');
        out.write('G');

        // PTS (32 bits) — we don't embed PTS at segment level in the raw ES;
        // the generator sets per-display-set PTS.  Write 0 here.
        writeU32(out, 0);
        // DTS (32 bits)
        writeU32(out, 0);

        // Segment type
        out.write(type.getCode());
        // Segment data length (16 bits)
        out.write((data.length >> 8) & 0xFF);
        out.write(data.length & 0xFF);
        // Segment data
        out.write(data);
    }

    /**
     * Writes a segment with explicit PTS/DTS in the PG header.
     */
    void writeSegmentWithPts(OutputStream out, IgsSegmentType type,
                             byte[] data, long pts, long dts) throws IOException {
        out.write('P');
        out.write('G');
        writeU32(out, (int) (pts & 0xFFFFFFFFL));
        writeU32(out, (int) (dts & 0xFFFFFFFFL));
        out.write(type.getCode());
        out.write((data.length >> 8) & 0xFF);
        out.write(data.length & 0xFF);
        out.write(data);
    }

    /**
     * Encodes a complete display set to the output stream with PTS in each
     * segment's PG header.
     */
    public void writeDisplaySet(OutputStream out, PgsDisplaySet displaySet, long pts)
            throws IOException {
        long dts = 0; // DTS is typically 0 for PGS

        // 1. PCS
        if (displaySet.getCompositionSegment() != null) {
            byte[] pcsData = encodePcs(displaySet.getCompositionSegment());
            writeSegmentWithPts(out, IgsSegmentType.PG_COMPOSITION, pcsData, pts, dts);
        }

        // 2. WDS
        if (displaySet.getWindowDefinitions() != null) {
            for (IgsWindowDefinition wds : displaySet.getWindowDefinitions()) {
                byte[] wdsData = encodeWindowDef(wds);
                writeSegmentWithPts(out, IgsSegmentType.WINDOW_DEFINITION, wdsData, pts, dts);
            }
        }

        // 3. PDS
        if (displaySet.getPalettes() != null) {
            for (IgsPalette pal : displaySet.getPalettes()) {
                byte[] pdsData = encodePalette(pal);
                writeSegmentWithPts(out, IgsSegmentType.PALETTE_DEFINITION, pdsData, pts, dts);
            }
        }

        // 4. ODS
        if (displaySet.getObjects() != null) {
            for (IgsObject obj : displaySet.getObjects()) {
                byte[] odsData = encodeObject(obj);
                writeSegmentWithPts(out, IgsSegmentType.OBJECT_DEFINITION, odsData, pts, dts);
            }
        }

        // 5. END_OF_DISPLAY
        writeSegmentWithPts(out, IgsSegmentType.END_OF_DISPLAY, new byte[0], pts, dts);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeU32(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
