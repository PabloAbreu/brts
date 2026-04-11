package org.brts.lowlevel.igs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Segment types found in IGS (Interactive Graphic Stream) and PGS (Presentation Graphic
 * Stream) elementary streams.
 * <p>
 * Each segment in the raw ES begins with a 1-byte type, a 2-byte length, and then
 * type-specific data. The constants here match the values defined in the Blu-ray
 * specification and implemented by libbluray.
 */
@RequiredArgsConstructor
@Getter
public enum IgsSegmentType {

	/** Palette Definition Segment (PDS). */
	PALETTE_DEFINITION(0x14),

	/** Object Definition Segment (ODS). */
	OBJECT_DEFINITION(0x15),

	/** Presentation Composition Segment (PCS) — used in PGS streams. */
	PG_COMPOSITION(0x16),

	/** Window Definition Segment (WDS). */
	WINDOW_DEFINITION(0x17),

	/** Interactive Composition Segment (ICS) — used in IGS streams. */
	IG_COMPOSITION(0x18),

	/** End of Display Set marker. */
	END_OF_DISPLAY(0x80);

	private final int code;

	/**
	 * Resolves a segment-type byte to an enum constant.
	 * @param b the raw byte value (0x14 – 0x80)
	 * @return the matching constant, or {@code null} if unknown
	 */
	public static IgsSegmentType fromByte(int b) {
		for (IgsSegmentType t : values()) {
			if (t.code == b)
				return t;
		}
		return null;
	}

}
