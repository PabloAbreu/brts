package org.brts.common.m2ts;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.common.utils.Crc32Utils;
import org.brts.lowlevel.igs.IgsParser;
import org.brts.lowlevel.igs.IgsSegmentType;
import org.brts.lowlevel.igs.model.IgsRawSegment;

import lombok.extern.slf4j.Slf4j;

/**
 * Muxes a raw IGS elementary stream file into a Blu-ray M2TS (192-byte source packet) container.
 *
 * <h2>Packet structure</h2>
 * <ul>
 * <li>Each IGS segment is wrapped in its own PES packet (stream_id=0xBD, PID 0x1400, stream_type=0x91).</li>
 * <li>PES payload = {@code [segment_type(1), segment_length_hi(1), segment_length_lo(1), segment_data...]}. No HDMV
 * sub-stream ID prefix is prepended; libbluray reads the segment type byte directly from the start of the PES
 * payload.</li>
 * <li>Every segment in a display set shares the same PTS, computed from the ATS at the start of that display set.</li>
 * </ul>
 *
 * <h2>ATS timing</h2> ATS values are fixed-increment (non-CBR): {@code ats(n) = ATS_START + n * ATS_INCREMENT}.
 * {@value #ATS_START} and {@value #ATS_INCREMENT} reproduce the arrival-time profile observed on commercial Blu-ray
 * discs with a single IGS sub-path clip.
 *
 * <h2>PSI/PCR</h2> PAT, PMT, and PCR packets are written once at the start and then every {@value #PSI_INTERVAL} source
 * packets.
 */
@Slf4j
public class M2tsIgsMuxer {

	// -------------------------------------------------------------------------
	// TS / Blu-ray constants
	// -------------------------------------------------------------------------

	private static final int TS_PACKET_SIZE = M2tsPacketUtils.TS_PACKET_SIZE;

	private static final int SOURCE_PKT_SIZE = 192;

	private static final int SYNC_BYTE = 0x47;

	// Blu-ray PIDs
	private static final int PAT_PID = 0x0000;

	private static final int PMT_PID = 0x0100;

	/** PCR PID — kept on a dedicated PID, separate from the IG stream (mirrors commercial disc layout). */
	private static final int PCR_PID = 0x1001;

	/** First Blu-ray IG stream PID. */
	private static final int IGS_PID = 0x1400;

	private static final int NULL_PID = 0x1FFF;

	/** ISO 13818-1 stream_type for HDMV Interactive Graphics. */
	private static final int IGS_STREAM_TYPE = 0x91;

	// -------------------------------------------------------------------------
	// timing parameters
	// most of these parameters are defined to reproduce the timing profile of a
	// commercial Blu-ray disc with a single IGS sub-path clip.
	// However, they seem to have little effect on the ability of VLC or PowerDVD to
	// decode and display the IGS stream.
	// -------------------------------------------------------------------------

	/**
	 * ATS (27 MHz) of the very first source packet.
	 */
	static final long ATS_START = 285_768_000L;

	static final long PTS_START = 1048560L;

	/** Fixed ATS increment per source packet (non-CBR constant-delivery schedule). */
	static final long ATS_INCREMENT = 2_118L;

	/** DTS is {@value} 90 kHz ticks earlier than PTS for each IGS packet. */
	private static final long DTS_PTS_OFFSET = 15_000L;

	static final long DTS_START = PTS_START - DTS_PTS_OFFSET;

	// -------------------------------------------------------------------------
	// PSI / PCR insertion interval
	// -------------------------------------------------------------------------

	/**
	 * Insert PAT + PMT + PCR once every {@value} source packets.
	 * <p>
	 * 2000 packets × 2118 ticks/packet ÷ 27 000 000 Hz ≈ 157 ms between PCR packets, well within the 500 ms Blu-ray
	 * maximum.
	 */
	private static final int PSI_INTERVAL = 2_000;

	// -------------------------------------------------------------------------
	// State (reset on each mux() call)
	// -------------------------------------------------------------------------

	/** Running count of source packets written to the current output stream. */
	private long totalPackets;

	// =========================================================================
	// Public API
	// =========================================================================

	/**
	 * Muxes the raw IGS elementary stream file {@code igsFile} into an M2TS file at {@code outputPath}.
	 * <p>
	 * The input file must contain bare IGS segments (no PES headers), as produced by
	 * {@link org.brts.common.m2ts.FilePacketHandler} or {@link org.brts.lowlevel.igs.IgsMuxer}.
	 *
	 * @param igsFile    path to the raw {@code .igs} file
	 * @param outputPath target {@code .m2ts} output path
	 * @throws IOException on I/O error
	 */
	public void mux(Path igsFile, Path outputPath) throws IOException {
		totalPackets = 0;

		List<IgsRawSegment> segments = new IgsParser().parseSegments(igsFile);

		log.info("M2tsIgsMuxer: {} → {} ({} segments)", igsFile.getFileName(), outputPath.getFileName(),
				segments.size());

		Files.createDirectories(outputPath.getParent());

		Map<Integer, Integer> cc = new LinkedHashMap<>();
		cc.put(PAT_PID, 0);
		cc.put(PMT_PID, 0);
		cc.put(PCR_PID, 0);
		cc.put(IGS_PID, 0);

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputPath), 1 << 20)) {

			// ── Initial PAT + PMT + PCR ───────────────────────────────────────
			writePsiAndPcr(out, cc);

			long packetsSincePsi = 0;

			for (IgsRawSegment seg : segments) {
				byte[] payload = buildSegmentPayload(seg);
				// on commercial blu-rays, palette segments have no DTS and are flagged as such in the PES header;
				// we mirror that behavior here
				boolean writeDts = seg.getType() != IgsSegmentType.PALETTE_DEFINITION
						&& seg.getType() != IgsSegmentType.END_OF_DISPLAY;
				long pts90 = pts(totalPackets);
				pts90 = seg.getType() == IgsSegmentType.PALETTE_DEFINITION ? DTS_START : pts90;
				byte[] pes = buildPesPacket(pts90, payload, writeDts);
				int written = writePesToTs(out, IGS_PID, pes, cc);
				packetsSincePsi += written;

				if (packetsSincePsi >= PSI_INTERVAL) {
					writePsiAndPcr(out, cc);
					packetsSincePsi = 0;
				}
			}

			// ── NULL-pad to 32-packet (6144-byte) aligned-unit boundary ───────
			long remainder = totalPackets % 32;
			if (remainder != 0) {
				long padCount = 32 - remainder;
				for (long i = 0; i < padCount; i++) {
					writeNullPacket(out);
				}
			}
		}

		log.info("M2tsIgsMuxer done: {} source packets ({} bytes)", totalPackets, totalPackets * SOURCE_PKT_SIZE);
	}

	private static long pts(long packetIndex) {
		return PTS_START + packetIndex * ATS_INCREMENT / 300L;
	}

	// =========================================================================
	// PES builders
	// =========================================================================

	/**
	 * Builds the PES payload for one IGS segment: the standard 3-byte segment header (type + 16-bit length) followed by
	 * the segment data bytes.
	 * <p>
	 * No HDMV sub-stream ID byte is prepended. libbluray's {@code _decode_segment()} reads the segment type byte
	 * directly from {@code p->buf[0]} without an intervening sub-stream identifier. Stream identification happens at
	 * the PMT level (stream_type=0x91).
	 */
	private byte[] buildSegmentPayload(IgsRawSegment seg) {
		byte[] data = seg.getSegmentData();
		int len = data != null ? data.length : 0;
		byte[] payload = new byte[3 + len];
		payload[0] = (byte) seg.getType().getCode();
		payload[1] = (byte) ((len >> 8) & 0xFF);
		payload[2] = (byte) (len & 0xFF);
		if (len > 0) {
			System.arraycopy(data, 0, payload, 3, len);
		}
		return payload;
	}

	/**
	 * Builds a PES packet for one IGS segment.
	 * <ul>
	 * <li>{@code stream_id} = 0xBD (private_stream_1)</li>
	 * <li>{@code PTS_DTS_flags} = 11 (PTS and DTS)</li>
	 * </ul>
	 *
	 * @param pts90   presentation time stamp in 90 kHz ticks
	 * @param payload segment payload bytes (type + length + data)
	 */
	private byte[] buildPesPacket(long pts90, byte[] payload, boolean writeDts) {
		long dts90 = pts90 - DTS_PTS_OFFSET;
		// PES header layout (19 bytes):
		// start_code_prefix(3) + stream_id(1) + packet_length(2)
		// + marker_bits+flags(2) + header_data_length(1) + PTS(5) + DTS(5)
		int headerLen = writeDts ? 19 : 14;
		// packet_length = bytes following the 6-byte mandatory header
		int pesLen = headerLen - 6 + payload.length;
		byte[] pes = new byte[headerLen + payload.length];

		pes[0] = 0x00; // start code prefix
		pes[1] = 0x00;
		pes[2] = 0x01;
		pes[3] = (byte) 0xBD; // stream_id = private_stream_1
		pes[4] = (byte) ((pesLen >> 8) & 0xFF);
		pes[5] = (byte) (pesLen & 0xFF);
		pes[6] = (byte) 0x80; // marker bits ('10')
		pes[7] = (byte) (writeDts ? 0xC0 : 0x80); // PTS_DTS_flags = '11' (PTS and DTS) or '10' (PTS only)
		pes[8] = (byte) (writeDts ? 0x0A : 0x05); // PES_header_data_length = 10 (PTS + DTS) or 5 (PTS only)

		// PTS encoding per ISO 13818-1 Table 2-21 (PTS_DTS_flags='11'):
		// byte: 0011 | PTS[32:30] | 1
		// PTS[29:22]
		// PTS[21:15] | 1
		// PTS[14:7]
		// PTS[6:0] | 1
		pes[9] = (byte) (0x31 | ((pts90 >> 29) & 0x0E));
		pes[10] = (byte) ((pts90 >> 22) & 0xFF);
		pes[11] = (byte) (0x01 | ((pts90 >> 14) & 0xFE));
		pes[12] = (byte) ((pts90 >> 7) & 0xFF);
		pes[13] = (byte) (0x01 | ((pts90 << 1) & 0xFE));

		// DTS encoding per ISO 13818-1 Table 2-21:
		// byte: 0001 | DTS[32:30] | 1
		// DTS[29:22]
		// DTS[21:15] | 1
		// DTS[14:7]
		// DTS[6:0] | 1
		if (writeDts) {
			pes[14] = (byte) (0x11 | ((dts90 >> 29) & 0x0E));
			pes[15] = (byte) ((dts90 >> 22) & 0xFF);
			pes[16] = (byte) (0x01 | ((dts90 >> 14) & 0xFE));
			pes[17] = (byte) ((dts90 >> 7) & 0xFF);
			pes[18] = (byte) (0x01 | ((dts90 << 1) & 0xFE));
		}

		System.arraycopy(payload, 0, pes, headerLen, payload.length);
		return pes;
	}

	// =========================================================================
	// TS multiplexing
	// =========================================================================

	/**
	 * Splits {@code pes} into 188-byte TS packets (with adaptation-field stuffing on the last packet) and writes
	 * 192-byte source packets to {@code out}.
	 *
	 * @return number of TS packets written
	 */
	private int writePesToTs(OutputStream out, int pid, byte[] pes, Map<Integer, Integer> cc) throws IOException {
		return M2tsPacketUtils.writePesToTs(pes, pid, cc, (ts, packetIndex) -> writeSourcePacket(out, ts));
	}

	// =========================================================================
	// PSI, PCR, and null packets
	// =========================================================================

	private void writePsiAndPcr(OutputStream out, Map<Integer, Integer> cc) throws IOException {
		writePatPacket(out, cc);
		writePmtPacket(out, cc);
		writePcrPacket(out, cc);
	}

	private void writePatPacket(OutputStream out, Map<Integer, Integer> cc) throws IOException {
		// PAT payload:
		// pointer(1) + table_id(1) + section_syntax+len(2) + ts_id(2) +
		// version+current(1) + sec_num(1) + last_sec_num(1) +
		// program_number(2) + PMT_PID(2) + CRC(4) = 17 bytes
		byte[] pat = new byte[17];
		pat[0] = 0x00; // pointer field
		pat[1] = 0x00; // table_id = PAT
		pat[2] = (byte) 0xB0;
		pat[3] = 0x0D; // section_length = 13
		pat[4] = 0x00;
		pat[5] = 0x01; // transport_stream_id = 1
		pat[6] = (byte) 0xC1; // version=0, current_next_indicator=1
		pat[7] = 0x00; // section_number
		pat[8] = 0x00; // last_section_number
		pat[9] = 0x00;
		pat[10] = 0x01; // program_number = 1
		pat[11] = (byte) (0xE0 | ((PMT_PID >> 8) & 0x1F));
		pat[12] = (byte) (PMT_PID & 0xFF);
		long crc = Crc32Utils.crc32(pat, 1, 12);
		pat[13] = (byte) ((crc >> 24) & 0xFF);
		pat[14] = (byte) ((crc >> 16) & 0xFF);
		pat[15] = (byte) ((crc >> 8) & 0xFF);
		pat[16] = (byte) (crc & 0xFF);

		writeTsPacket(out, PAT_PID, true, ccNext(cc, PAT_PID), pat);
	}

	private void writePmtPacket(OutputStream out, Map<Integer, Integer> cc) throws IOException {
		byte[] esDesc = new byte[0];

		// PMT layout (fixed for a single IGS stream, esDesc.length = 10):
		// pointer(1) + table_id(1) + section_len(2) = 4 header bytes
		// section content:
		// program_number(2) + version/current(1) + sec_num(1) + last_sec_num(1) +
		// PCR_PID(2) + program_info_len(2) = 9 bytes
		// ES entry: stream_type(1) + ES_PID(2) + ES_info_len(2) + esDesc
		// = 5 + esDesc.length bytes
		// CRC(4)
		int esEntryLen = 5 + esDesc.length;
		int sectionLen = 9 + esEntryLen + 4;
		byte[] pmt = new byte[4 + sectionLen];

		pmt[0] = 0x00; // pointer field
		pmt[1] = 0x02; // table_id = PMT
		pmt[2] = (byte) (0xB0 | ((sectionLen >> 8) & 0x0F));
		pmt[3] = (byte) (sectionLen & 0xFF);
		pmt[4] = 0x00; // program_number high
		pmt[5] = 0x01; // program_number low
		pmt[6] = (byte) 0xC1; // version=0, current_next_indicator=1
		pmt[7] = 0x00; // section_number
		pmt[8] = 0x00; // last_section_number
		pmt[9] = (byte) (0xE0 | ((PCR_PID >> 8) & 0x1F));
		pmt[10] = (byte) (PCR_PID & 0xFF);
		pmt[11] = (byte) 0xF0; // program_info_length (reserved=1111) + high bits = 0
		pmt[12] = 0x00; // program_info_length low = 0 (no program descriptors)

		// ES entry
		pmt[13] = (byte) IGS_STREAM_TYPE;
		pmt[14] = (byte) (0xE0 | ((IGS_PID >> 8) & 0x1F));
		pmt[15] = (byte) (IGS_PID & 0xFF);
		pmt[16] = (byte) (0xF0 | ((esDesc.length >> 8) & 0x0F));
		pmt[17] = (byte) (esDesc.length & 0xFF);
		System.arraycopy(esDesc, 0, pmt, 18, esDesc.length);

		// CRC: from table_id (pmt[1]) through last ES descriptor byte (pmt[18 + esDesc.length - 1])
		int crcDataLen = 18 + esDesc.length - 1;
		long crc = Crc32Utils.crc32(pmt, 1, crcDataLen);
		int crcOff = 18 + esDesc.length;
		pmt[crcOff] = (byte) ((crc >> 24) & 0xFF);
		pmt[crcOff + 1] = (byte) ((crc >> 16) & 0xFF);
		pmt[crcOff + 2] = (byte) ((crc >> 8) & 0xFF);
		pmt[crcOff + 3] = (byte) (crc & 0xFF);

		writeTsPacket(out, PMT_PID, true, ccNext(cc, PMT_PID), pmt);
	}

	private void writePcrPacket(OutputStream out, Map<Integer, Integer> cc) throws IOException {
		long pcr27 = ats(totalPackets);
		writeSourcePacket(out, M2tsPacketUtils.buildPcrPacket(PCR_PID, pcr27, cc));
	}

	private void writeNullPacket(OutputStream out) throws IOException {
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((NULL_PID >> 8) & 0x1F);
		ts[2] = (byte) (NULL_PID & 0xFF);
		ts[3] = (byte) 0x10; // payload only, CC = 0 (null packets never increment CC)
		Arrays.fill(ts, 4, TS_PACKET_SIZE, (byte) 0xFF);
		writeSourcePacket(out, ts);
	}

	private void writeTsPacket(OutputStream out, int pid, boolean pusi, int ccVal, byte[] payload) throws IOException {
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((pusi ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
		ts[2] = (byte) (pid & 0xFF);
		ts[3] = (byte) (0x10 | (ccVal & 0x0F)); // payload only
		int copy = Math.min(payload.length, TS_PACKET_SIZE - 4);
		System.arraycopy(payload, 0, ts, 4, copy);
		Arrays.fill(ts, 4 + copy, TS_PACKET_SIZE, (byte) 0xFF);
		writeSourcePacket(out, ts);
	}

	// =========================================================================
	// Source packet output
	// =========================================================================

	/**
	 * Prepends a 4-byte {@code TP_extra_header} containing the 30-bit ATS to {@code tsPacket} and writes the resulting
	 * 192-byte source packet to {@code out}. Increments {@link #totalPackets} after the write.
	 */
	private void writeSourcePacket(OutputStream out, byte[] tsPacket) throws IOException {
		// TP_extra_header: copy_permission_indicator(2) + ATS(30), in network byte order.
		// copy_permission = '00' (copy-free).
		long ats30 = ats(totalPackets) & 0x3FFF_FFFFL;
		out.write((int) ((ats30 >>> 24) & 0xFF));
		out.write((int) ((ats30 >>> 16) & 0xFF));
		out.write((int) ((ats30 >>> 8) & 0xFF));
		out.write((int) (ats30 & 0xFF));
		out.write(tsPacket);
		totalPackets++;
	}

	// =========================================================================
	// ATS computation
	// =========================================================================

	private static long ats(long packetIndex) {
		return ATS_START + packetIndex * ATS_INCREMENT;
	}

	// =========================================================================
	// Continuity counter
	// =========================================================================

	private int ccNext(Map<Integer, Integer> cc, int pid) {
		int val = cc.getOrDefault(pid, 0);
		cc.put(pid, (val + 1) & 0x0F);
		return val;
	}

}
