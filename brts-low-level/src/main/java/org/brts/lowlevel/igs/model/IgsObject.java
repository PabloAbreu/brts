package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Object Definition Segment (ODS) fragment, or a logical RLE-compressed bitmap reconstructed from its fragments, used
 * for button graphics or subtitle imagery.
 * <p>
 * When the object spans multiple segments, each fragment carries the same {@link #id} and the
 * {@link #sequenceDescriptor} indicates first/last. Only the first fragment carries {@link #dataLength}, {@link #width}
 * and {@link #height}. The complete RLE data is the concatenation of all fragments' {@link #rleData}.
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

	/** Total logical data length (24 bits), present only on the first fragment. */
	private int dataLength;

	/** Width in pixels, present only on the first fragment. */
	private int width;

	/** Height in pixels, present only on the first fragment. */
	private int height;

	/**
	 * Raw RLE-encoded bitmap bytes. This contains one physical fragment in parser or producer display sets and the
	 * complete bitmap in objects returned by the IGS demuxer.
	 */
	private byte[] rleData;

}
