package org.brts.lowlevel.model.clpi;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * EP_map (Entry Point Map) — enables random access into an M2TS clip. Each {@link EpMapEntry} stores a coarse-grained
 * (SPN=Source Packet Number) anchor aligned to a keyframe, required for seek and chapter navigation.
 */
@Getter
@Setter
public class EpMap {

	/** One EP_map stream entry per video PID (usually just one). */
	private List<EpMapStream> streams;

	// -------------------------------------------------------------------------

	/** EP_map stream — groups all entry points for one elementary stream PID. */
	@Getter
	@Setter
	public static class EpMapStream {

		private int pid;

		/** EP type: 1 = I-frame only (standard for video). */
		private int epType = 1;

		private List<EpMapEntry> entries;

	}

	// -------------------------------------------------------------------------

	/**
	 * A single entry-point anchor within an EP_map stream. The combination of PTS + SPN uniquely identifies a seekable
	 * position.
	 */
	@Getter
	@Setter
	public static class EpMapEntry {

		/** Presentation Time Stamp in 90 kHz ticks. */
		private long ptsTicks;

		/** Source Packet Number (188-byte TS packet index from start of clip). */
		private long spn;

		/** True if this entry marks a multi-angle change point. */
		private boolean isAngleChangePoint;

		/**
		 * I-picture end position offset (3 bits, 0–7). Indicates the relative distance to the end of the I-picture in
		 * units of aligned units.
		 */
		private int iEndPositionOffset;

	}

}
