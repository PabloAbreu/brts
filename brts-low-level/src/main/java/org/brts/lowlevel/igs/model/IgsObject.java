package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Object Definition Segment (ODS) — a (possibly fragmented) RLE-compressed
 * bitmap used for button graphics or subtitle imagery.
 * <p>
 * When the object spans multiple segments, each fragment carries the same
 * {@link #id} and the {@link #sequenceDescriptor} indicates first/last.
 * The complete RLE data is the concatenation of all fragments' {@link #rleData}.
 */
@Getter
@Setter
@ToString(exclude = "rleData")
public class IgsObject {
    /** PTS from the PES header (90 kHz ticks). */
    private long pts;
    /** Object id (16 bits). */
    private int id;
    /** Object version number (8 bits). */
    private int version;
    /** Sequence descriptor (first/last fragment flags). */
    private SequenceDescriptor sequenceDescriptor;
    /** Total data length (24 bits), from the first fragment. */
    private int dataLength;
    /** Width in pixels (from the first fragment). */
    private int width;
    /** Height in pixels (from the first fragment). */
    private int height;
    /**
     * Raw RLE-encoded bitmap bytes. For a single-segment object this is the
     * complete bitmap. For multi-segment objects, the caller must concatenate
     * all fragments.
     */
    private byte[] rleData;
}
