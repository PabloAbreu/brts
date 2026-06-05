package org.brts.lowlevel.m2ts;

/**
 * Renders M2TS dump output in a specific format. Implementations decide how to present the header and each packet-group
 * row (e.g. human-readable table, CSV, …).
 */
public interface M2tsDumpFormatter {

	/**
	 * Prints the column header line(s) before any rows are emitted.
	 */
	void printHeader();

	/**
	 * Prints a single row representing a consecutive group of packets sharing the same PID.
	 *
	 * @param groupStartPacket index of the first packet in the group (0-based)
	 * @param groupCount       number of packets in this group
	 * @param packetsSoFar     cumulative packet count for this PID up to and including this group
	 * @param groupAts         ATS value of the first packet in the group (27 MHz ticks)
	 * @param pid              the PID shared by all packets in the group
	 * @param streamType       human-readable stream type label (e.g. "VIDEO", "AUDIO", "PAT", …)
	 * @param groupPts         PTS of the first PES in this group, or -1 if unavailable
	 * @param groupDts         DTS of the first PES in this group, or -1 if unavailable
	 * @param groupPesLength   PES_packet_length field value, or -1 if not a PES packet
	 */
	void printRow(long groupStartPacket, long groupCount, long packetsSoFar, long groupAts, int pid, String streamType,
			long groupPts, long groupDts, long groupPesLength);

	/**
	 * Called after the last row has been emitted. Implementations may flush buffered output or print a footer here.
	 */
	void close();

}
