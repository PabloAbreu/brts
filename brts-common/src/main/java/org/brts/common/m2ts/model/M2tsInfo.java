package org.brts.common.m2ts.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Top-level result of inspecting an M2TS file.
 * <p>
 * Produced by {@link org.brts.lowlevel.m2ts.M2tsParser} and serialisable to JSON so CLI
 * users can inspect a file without further tooling.
 */
@Getter
@Setter
@ToString
public class M2tsInfo {

	/** Absolute path to the parsed file. */
	private String sourcePath;

	/**
	 * Total number of 192-byte Source Packets in the file (i.e. file size / 192, rounded
	 * down).
	 */
	private long totalPackets;

	/**
	 * First arrival timestamp (ATS) seen in the TP_extra_header, in 27 MHz ticks. A value
	 * of {@code -1} means no packets were read.
	 */
	private long firstAts27MHz = -1;

	/**
	 * Last arrival timestamp (ATS) seen, in 27 MHz ticks.
	 */
	private long lastAts27MHz = -1;

	/**
	 * First PCR (Program Clock Reference) value seen, in 27 MHz ticks. {@code -1} if no
	 * PCR packet was found.
	 */
	private long firstPcr27MHz = -1;

	/**
	 * Last PCR value seen, in 27 MHz ticks.
	 */
	private long lastPcr27MHz = -1;

	/**
	 * Program Map Table PID (normally 0x100 = 256 on Blu-ray).
	 */
	private int pmtPid = -1;

	/**
	 * PCR PID reported by the PMT.
	 */
	private int pcrPid = -1;

	/**
	 * All elementary streams found in the PMT, in PMT order.
	 */
	private List<M2tsStreamInfo> streams;

	// -------------------------------------------------------------------------
	// Convenience helpers
	// -------------------------------------------------------------------------

	/**
	 * Duration derived from first/last PCR, converted to milliseconds. Returns {@code -1}
	 * if PCR data is not available.
	 */
	@Deprecated
	// because of wrap-around, this will never work as is
	public long __getDurationMs() {
		if (firstPcr27MHz < 0 || lastPcr27MHz < 0)
			return -1;
		long deltaTicks = lastPcr27MHz - firstPcr27MHz;
		return deltaTicks / 27_000; // 27 MHz → ms
	}

}
