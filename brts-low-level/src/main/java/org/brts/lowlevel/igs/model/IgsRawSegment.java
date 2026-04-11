package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.brts.lowlevel.igs.IgsSegmentType;

/**
 * A raw PES-encapsulated segment as found in an IGS elementary stream.
 * <p>
 * The IGS stream is made up of PES packets. Each PES packet carries a PTS/DTS header
 * followed by one segment. A segment has a 3-byte header (type + length) plus the segment
 * data bytes.
 * <p>
 * This class preserves the exact binary representation to enable faithful round-trip
 * (demux → mux) without loss.
 */
@Getter
@Setter
@ToString(exclude = "segmentData")
public class IgsRawSegment {

	/** PTS from the enclosing PES header (90 kHz ticks). */
	private long pts;

	/** DTS from the enclosing PES header (90 kHz ticks, -1 if absent). */
	private long dts = -1;

	/** Segment type byte. */
	private IgsSegmentType type;

	/** Raw segment data (excluding the 3-byte type+length header). */
	private byte[] segmentData;

}
