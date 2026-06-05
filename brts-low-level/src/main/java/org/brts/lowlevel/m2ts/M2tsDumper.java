package org.brts.lowlevel.m2ts;

import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads an M2TS file and emits grouped packet-level information to a {@link M2tsDumpFormatter}.
 * <p>
 * Consecutive packets sharing the same PID are merged into a single group. For each group the PES header is decoded
 * when available to extract PTS/DTS values.
 */
public class M2tsDumper {

	private final M2tsInfo info;

	private final M2tsDumpFormatter formatter;

	public M2tsDumper(M2tsInfo info, M2tsDumpFormatter formatter) {
		this.info = info;
		this.formatter = formatter;
	}

	/**
	 * Runs the dump over the given packet range and outputs rows via the formatter.
	 *
	 * @param inputPath  source M2TS file
	 * @param start      first packet index to process (0-based, inclusive)
	 * @param stop       last packet index to process (inclusive)
	 * @param keepTables whether to include PAT/PMT/PCR PIDs in the output
	 */
	public void dump(Path inputPath, long start, long stop, boolean keepTables) throws Exception {
		formatter.printHeader();
		try (GroupingHandler handler = new GroupingHandler()) {
			new M2tsDemuxer().demux(inputPath, info, handler, null, start, stop, keepTables);
		}
		formatter.close();
	}

	// -------------------------------------------------------------------------
	// Private grouping state machine
	// -------------------------------------------------------------------------

	private class GroupingHandler implements M2tsPacketHandler {

		private final Map<Integer, M2tsStreamInfo> pidToStreamInfo = new LinkedHashMap<>();

		private int previousPid = -1;

		private long groupStartPacket;

		private long groupCount;

		private long groupAts;

		private long groupPts;

		private long groupDts;

		private long groupPesLength;

		private final Map<Integer, Long> pidToPacketCount = new HashMap<>();

		GroupingHandler() {
			if (info.getStreams() != null) {
				for (M2tsStreamInfo s : info.getStreams()) {
					pidToStreamInfo.put(s.getPid(), s);
				}
			}
		}

		@Override
		public void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart,
				long packetIndex, long ats) throws IOException {
			if (pid != previousPid) {
				flushGroup();
				groupStartPacket = packetIndex;
				groupCount = 1;
				groupAts = ats;
				groupPesLength = -1;

				if (payloadUnitStart && detectPesStart(payload, offset, length)) {
					groupPesLength = ((payload[offset + 4] & 0xFF) << 8) | (payload[offset + 5] & 0xFF);
					if (length >= 12) {
						long pts = parsePts(payload, offset + 6);
						long dts = parseDts(payload, offset + 6);
						if (pts >= 0) {
							groupPts = pts;
						}
						if (dts >= 0) {
							groupDts = dts;
						}
					}
				} else {
					groupPts = -1;
					groupDts = -1;
				}
			} else {
				groupCount++;
			}
			previousPid = pid;
		}

		@Override
		public void close() {
			flushGroup();
		}

		private void flushGroup() {
			if (previousPid < 0) {
				return;
			}
			M2tsStreamInfo stream = pidToStreamInfo.get(previousPid);
			String streamType = "UNKNOWN";
			if (stream != null) {
				StreamCodingType codingType = stream.getCodingType();
				if (codingType != null) {
					streamType = stream.getCategory();
				}
			} else {
				if (previousPid == 0) {
					streamType = "PAT";
				} else if (previousPid == info.getPmtPid()) {
					streamType = "PMT";
				} else if (previousPid == info.getPcrPid()) {
					streamType = "PCR";
				} else if (previousPid == info.getSitPid()) {
					streamType = "SIT";
				} else if (previousPid == 0x1FFF) {
					streamType = "NULL";
				}
			}
			long packetsSoFar = pidToPacketCount.compute(previousPid,
					(k, v) -> (v == null) ? groupCount : v + groupCount);
			formatter.printRow(groupStartPacket, groupCount, packetsSoFar, groupAts, previousPid, streamType, groupPts,
					groupDts, groupPesLength);
		}

		private boolean detectPesStart(byte[] payload, int offset, int length) {
			return length >= 6 && payload[offset] == 0x00 && payload[offset + 1] == 0x00 && payload[offset + 2] == 0x01;
		}

		private long parsePts(byte[] payload, int offset) {
			int ptsDtsFlags = payload[offset + 1] & 0xC0;
			if ((ptsDtsFlags & 0x80) != 0) {
				return parsePtsOrDtsTimestamp(payload, offset + 3);
			}
			return -1;
		}

		private long parseDts(byte[] payload, int offset) {
			int ptsDtsFlags = payload[offset + 1] & 0xC0;
			if ((ptsDtsFlags & 0x40) != 0) {
				return parsePtsOrDtsTimestamp(payload, offset + 8);
			}
			return -1;
		}

		private static long parsePtsOrDtsTimestamp(byte[] data, int offset) {
			return ((long) (data[offset] & 0x0E) << 29) | ((data[offset + 1] & 0xFF) << 22)
					| ((data[offset + 2] & 0xFE) << 14) | ((data[offset + 3] & 0xFF) << 7)
					| ((data[offset + 4] & 0xFE) >> 1);
		}

	}

}
