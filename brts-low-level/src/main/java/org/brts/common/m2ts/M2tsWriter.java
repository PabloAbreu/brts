package org.brts.common.m2ts;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This code was created by IA to mux ES into m2ts. But the result is still not playable.
 * It needs to be fixed to support IGS muxing along with video.
 *
 *
 * Creates an M2TS (Blu-ray 192-byte source packet) file from a set of elementary stream
 * (ES) files on disk and a list of chapter timestamps.
 *
 * <h2>What the writer does</h2>
 * <ol>
 * <li>Reads each ES file, packetises the raw bytes into PES packets.</li>
 * <li>Multiplexes all PES packets into 188-byte MPEG-2 TS packets.</li>
 * <li>Prepends each TS packet with a 4-byte {@code TP_extra_header} containing a
 * synthesised 27 MHz Arrival Time Stamp (ATS), producing the standard 192-byte Blu-ray
 * source packet.</li>
 * <li>Periodically inserts PAT and PMT tables.</li>
 * <li>Inserts PCR packets on the video PID so players can lock their clocks.</li>
 * <li>Tracks the Source Packet Number (SPN) at each chapter / IDR boundary and builds the
 * data needed by {@link org.brts.lowlevel.writer.ClipInfoWriter}.</li>
 * </ol>
 *
 * <h2>ClipInfo generation</h2> After writing the M2TS file, call
 * {@link #buildClipInfo(String)} to obtain a fully-populated {@link ClipInfo} model that
 * can be serialised directly by {@link org.brts.lowlevel.writer.ClipInfoWriter}.
 *
 * <h2>Limitations / assumptions</h2>
 * <ul>
 * <li>ES files are treated as raw byte streams; no re-encoding is performed.</li>
 * <li>PES timestamps are synthesised from the chapter list at a constant frame rate
 * derived from the first video stream; individual frame-accurate timestamps are not
 * parsed from the ES bitstream.</li>
 * <li>Audio/subtitle PES packets are interleaved round-robin with video at a fixed
 * byte-budget per video frame, which is sufficient for authoring but not broadcast-grade
 * multiplexing.</li>
 * </ul>
 */
public class M2tsWriter {

	static final Logger log = LoggerFactory.getLogger(M2tsWriter.class);

	// TS constants
	private static final int TS_PACKET_SIZE = 188;

	private static final int SOURCE_PKT_SIZE = 192;

	private static final int SYNC_BYTE = 0x47;

	// Blu-ray default PIDs
	private static final int PMT_PID = 0x0100; // 256

	private static final int PCR_PID_DEFAULT = 0x1001; // overridden by first video PID

	// PSI / PCR insertion intervals
	private static final int PAT_PMT_INTERVAL_PACKETS = 200; // insert PAT+PMT every N TS
																// packets

	private static final long PCR_INTERVAL_27MHZ = 27_000_000L / 25; // ~40 ms at 27 MHz

	// CBR mux constants
	private static final int NULL_PID = 0x1FFF;

	private static final int DEFAULT_TARGET_BITRATE_KBPS = 30_000;

	// 192 bytes × 8 bits × 27 000 000 Hz = 41 472 000 000
	// ATS for source packet n (in 27 MHz ticks) = n × ATS_NUMERATOR / targetBitrateBps
	private static final long ATS_NUMERATOR = 192L * 8L * 27_000_000L;

	// = ATS_NUMERATOR / 300; used to compute expected packet count from 90 kHz PTS
	// without overflow
	private static final long EXPECTED_PACKETS_DIVISOR = 192L * 8L * 90_000L;

	// PES header constants

	// HDMV private_stream_1 (0xBD) sub-stream IDs
	// Blu-ray spec: first byte of the PES data payload identifies the sub-stream type.
	static final int SUBSTREAM_ID_IG = 0x20; // first IGS sub-stream
	static final int SUBSTREAM_ID_PG = 0x00; // first PGS sub-stream
	static final int SUBSTREAM_ID_AUDIO_PRIVATE = 0x80; // first private audio
														// (AC3/DTS/LPCM)

	// -------------------------------------------------------------------------
	// State built during write()
	// -------------------------------------------------------------------------

	private long totalTsPackets = 0;

	private long firstPts90kHz = 0;

	private long lastPts90kHz = 0;

	private long firstPcr27MHz = -1;

	private long targetBitrateBps = DEFAULT_TARGET_BITRATE_KBPS * 1000L;

	private final List<EpMap.EpMapEntry> epEntries = new ArrayList<>();

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Writes the M2TS file described by {@code descriptor} to {@code outputPath}.
	 * @param descriptor mux descriptor (streams + chapters)
	 * @param outputPath target {@code .m2ts} file path
	 * @throws IOException on I/O error
	 */
	public void write(M2tsDescriptor descriptor, Path outputPath) throws IOException {
		// Reset state
		totalTsPackets = 0;
		firstPts90kHz = 0;
		lastPts90kHz = 0;
		firstPcr27MHz = -1;
		targetBitrateBps = (descriptor.getTargetBitrateKbps() != null ? (long) descriptor.getTargetBitrateKbps()
				: DEFAULT_TARGET_BITRATE_KBPS) * 1000L;
		epEntries.clear();

		Files.createDirectories(outputPath.getParent());

		List<M2tsDescriptor.StreamEntry> streams = descriptor.getStreams();
		if (streams == null || streams.isEmpty())
			throw new IllegalArgumentException("No streams in descriptor");

		// Determine video PID (first video stream)
		int videoPid = -1;
		for (M2tsDescriptor.StreamEntry s : streams) {
			StreamCodingType ct = M2tsParser.mapStreamType(s.getStreamTypeByte());
			if (ct != null && ct.isVideo()) {
				videoPid = s.getPid();
				break;
			}
		}
		int pcrPid = videoPid >= 0 ? videoPid : PCR_PID_DEFAULT;

		// Build continuity counters (one per PID)
		Map<Integer, Integer> cc = new LinkedHashMap<>();
		for (M2tsDescriptor.StreamEntry s : streams)
			cc.put(s.getPid(), 0);
		cc.put(0x0000, 0); // PAT
		cc.put(PMT_PID, 0); // PMT

		// Chapter map: PTS (90 kHz) → chapter
		// Chapter ptsTicks in the descriptor are specified as logical display times
		// relative to the stream start (ptsTicks=0 = first frame). If an initial
		// PTS offset is configured the map keys are shifted by that amount so they
		// match the on-disc PTS values that the mux loop compares against.
		List<M2tsChapter> chapters = descriptor.getChapters() != null ? descriptor.getChapters() : List.of();
		long initialPtsOffset = (descriptor.getInitialPtsOffsetTicks() != null
				&& descriptor.getInitialPtsOffsetTicks() > 0) ? descriptor.getInitialPtsOffsetTicks() : 0L;
		Map<Long, M2tsChapter> chapterByPts = new LinkedHashMap<>();
		for (M2tsChapter ch : chapters)
			chapterByPts.put(ch.getPtsTicks() + initialPtsOffset, ch);

		log.info("Writing M2TS: {} → {}", descriptor.getOutputName(), outputPath);

		try (OutputStream rawOut = new BufferedOutputStream(Files.newOutputStream(outputPath), 1 << 20)) {

			// Open all ES input streams (frame-aware readers)
			List<ESReader> readers = new ArrayList<>();
			for (M2tsDescriptor.StreamEntry s : streams) {
				readers.add(new ESReader(s, Path.of(s.getFile())));
			}

			// Find the (first) video reader — needed for B-frame timeline adjustments
			ESReader videoReader = readers.stream().filter(ESReader::isVideo).findFirst().orElse(null);

			// 90 kHz PTS — start at a standard Blu-ray origin
			long pts90 = (descriptor.getInitialPtsOffsetTicks() != null && descriptor.getInitialPtsOffsetTicks() > 0)
					? descriptor.getInitialPtsOffsetTicks() : 0L;
			firstPts90kHz = pts90;

			long nextPcrAts = 0;
			int pktSincePsi = PAT_PMT_INTERVAL_PACKETS; // trigger immediate write

			// Per-stream PTS tracking (video and each audio advance at their own rate).
			// For H.264 streams with B-frames the ESReader supplies pre-computed
			// per-frame PTS/DTS; the streamPts entry is still advanced so that the
			// master pts90 clock remains correct for all non-B-frame streams.
			Map<Integer, Long> streamPts = new LinkedHashMap<>();
			Map<Integer, Long> streamFrameDuration = new LinkedHashMap<>();
			for (ESReader reader : readers) {
				streamPts.put(reader.pid(), pts90);
				streamFrameDuration.put(reader.pid(), reader.frameDuration90kHz());
			}

			boolean anyRemaining = true;

			while (anyRemaining) {
				// --- PSI (PAT + PMT) ---
				if (pktSincePsi >= PAT_PMT_INTERVAL_PACKETS) {
					writePatPacket(rawOut, PMT_PID, cc, atsOf(totalTsPackets));
					totalTsPackets++;
					writePmtPacket(rawOut, streams, pcrPid, cc, atsOf(totalTsPackets));
					totalTsPackets++;
					pktSincePsi = 0;
				}

				// --- PCR ---
				if (pcrPid >= 0 && atsOf(totalTsPackets) >= nextPcrAts) {
					// PCR is derived from the *elapsed* PTS (relative to mux start, t=0)
					// using the ATS itself as the reference clock. Using the on-disc
					// pts90 (which is offset by initialPtsOffset) would produce a PCR
					// value that is far ahead of the actual ATS, causing demuxers to
					// report all subsequent PES packets as arriving "late".
					long pcr27 = atsOf(totalTsPackets); // ATS IS the 27 MHz delivery
														// clock
					writePcrPacket(rawOut, pcrPid, pcr27, cc, atsOf(totalTsPackets));
					if (firstPcr27MHz < 0)
						firstPcr27MHz = pcr27;
					totalTsPackets++;
					nextPcrAts = atsOf(totalTsPackets) + PCR_INTERVAL_27MHZ;
					pktSincePsi++;
				}

				// --- EP_map / chapter anchor ---
				// For H.264 B-frame streams the chapter check uses the display PTS of
				// the upcoming video frame (without the reorder-delay offset) so that
				// descriptors can specify chapter positions as logical display PTS
				// (e.g. ptsTicks=0 for the first IDR). The EP_map entry records the
				// actual PES PTS (with the offset) so that player seeking is correct.
				// The chapterByPts map has already been shifted by initialPtsOffset so
				// both the non-B-frame (pts90) and B-frame (getDisplayPts + offset) paths
				// produce the correct on-disc PTS key.
				long currentSpn = totalTsPackets;
				long chapterCheckPts = (videoReader != null && videoReader.hasBFrames())
						? videoReader.getDisplayPts(videoReader.getFrameIndex()) + firstPts90kHz : pts90;
				if (chapterByPts.containsKey(chapterCheckPts)) {
					long epPts = (videoReader != null && videoReader.hasBFrames())
							? videoReader.getPts(videoReader.getFrameIndex()) + firstPts90kHz : pts90;
					EpMap.EpMapEntry epEntry = new EpMap.EpMapEntry();
					epEntry.setPtsTicks(epPts);
					epEntry.setSpn(currentSpn);
					epEntries.add(epEntry);
					log.debug("EP_map entry: PTS={} SPN={}", epPts, currentSpn);
				}

				// --- Mux one access unit (frame) per stream ---
				anyRemaining = false;
				for (ESReader reader : readers) {
					if (!reader.isDone()) {
						// Capture the decode-index before advancing it via nextFrame()
						int decodeIdx = reader.getFrameIndex();
						byte[] frame = reader.nextFrame();
						if (frame != null && frame.length > 0) {
							long thisPts;
							Long thisDts; // null → PTS-only PES header
							if (reader.hasBFrames()) {
								// ESReader computes PTS/DTS relative to t=0; add the
								// initial
								// PTS offset so they align with the master pts90 clock.
								thisPts = reader.getPts(decodeIdx) + firstPts90kHz;
								long dts = reader.getDts(decodeIdx) + firstPts90kHz;
								thisDts = dts; // always write DTS for H.264
							}
							else {
								thisPts = streamPts.get(reader.pid());
								// H.264 requires PTS+DTS even when they are equal
								// (decoder HRD model)
								thisDts = reader.isH264() ? thisPts : null;
								long fd = streamFrameDuration.get(reader.pid());
								streamPts.put(reader.pid(), thisPts + fd);
							}
							byte[] pes = buildPesPacket(reader.streamId(), reader.substreamId(), thisPts, thisDts,
									frame);
							int written = writePesToTs(rawOut, reader.pid(), pes, cc, totalTsPackets);
							totalTsPackets += written;
							pktSincePsi += written;
							anyRemaining = true;
						}
						if (!reader.isDone())
							anyRemaining = true;
					}
				}

				// Advance the master PTS by the video frame duration
				lastPts90kHz = pts90;
				long videoFd = readers.stream()
					.filter(ESReader::isVideo)
					.mapToLong(ESReader::frameDuration90kHz)
					.findFirst()
					.orElse(90_000L / 24);
				pts90 += videoFd;

				// CBR null-packet stuffing: insert PID 0x1FFF packets so that the total
				// source packet count matches what should have been delivered by the end
				// of this video frame slot at the target bitrate.
				// Subtract the initial PTS offset so that the elapsed-time computation
				// is relative to the start of muxing (t=0), not the on-disc PTS origin.
				long elapsedPts90 = pts90 - firstPts90kHz;
				long frameEndExpected = (elapsedPts90 * targetBitrateBps + EXPECTED_PACKETS_DIVISOR - 1)
						/ EXPECTED_PACKETS_DIVISOR;
				while (totalTsPackets < frameEndExpected) {
					writeNullPacket(rawOut, atsOf(totalTsPackets));
					totalTsPackets++;
				}
			}

			// Ensure all readers are closed
			for (ESReader r : readers)
				r.close();
		}

		log.info("M2TS written: {} TS packets ({} bytes)", totalTsPackets, totalTsPackets * (long) SOURCE_PKT_SIZE);
	}

	/**
	 * Builds a {@link ClipInfo} model populated with the timing information collected
	 * during the most recent {@link #write} call.
	 * <p>
	 * This model can be written directly by
	 * {@link org.brts.lowlevel.writer.ClipInfoWriter}.
	 * @param descriptor the same descriptor that was passed to {@link #write}
	 * @param clipName 5-digit clip name (e.g. "00001")
	 * @return a fully populated {@link ClipInfo}
	 */
	public ClipInfo buildClipInfo(M2tsDescriptor descriptor, String clipName) {
		ClipInfo info = new ClipInfo();
		info.setClipName(clipName);
		info.setClipStreamType(1); // AV clip
		info.setApplicationType(1); // movie

		// PTS values stored in ClipInfo are 45 kHz (spec stores /2 of 90 kHz)
		Timestamp startTs = Timestamp.ofTicks(firstPts90kHz);
		Timestamp endTs = Timestamp.ofTicks(lastPts90kHz);
		info.setTsRecordingStartPts(startTs);
		info.setTsRecordingEndPts(endTs);
		info.setDuration(Timestamp.ofTicks(lastPts90kHz - firstPts90kHz));

		// Build ClipStreams from descriptor
		List<ClipStream> clipStreams = new ArrayList<>();
		for (M2tsDescriptor.StreamEntry se : descriptor.getStreams()) {
			ClipStream cs = new ClipStream();
			cs.setPid(se.getPid());
			StreamCodingType ct = M2tsParser.mapStreamType(se.getStreamTypeByte());
			cs.setCodingType(ct);
			cs.setLanguage(se.getLanguage());
			// Basic attribute defaults — the caller can refine these
			if (ct != null) {
				if (ct.isVideo()) {
					cs.setVideoFormat(6); // 1080p default
					cs.setFrameRate(2); // 24 fps default
					cs.setAspectRatio(3); // 16:9 default
				}
				else if (ct.isAudio()) {
					cs.setAudioChannelLayout(3); // stereo default
					cs.setSampleRate(1); // 48 kHz default
				}
			}
			clipStreams.add(cs);
		}
		info.setStreams(clipStreams);

		// Build EP_map (video PID only)
		if (!epEntries.isEmpty()) {
			int videoPid = -1;
			for (M2tsDescriptor.StreamEntry s : descriptor.getStreams()) {
				StreamCodingType ct = M2tsParser.mapStreamType(s.getStreamTypeByte());
				if (ct != null && ct.isVideo()) {
					videoPid = s.getPid();
					break;
				}
			}
			if (videoPid >= 0) {
				EpMap.EpMapStream eps = new EpMap.EpMapStream();
				eps.setPid(videoPid);
				eps.setEpType(1); // I-frame only
				eps.setEntries(new ArrayList<>(epEntries));

				EpMap epMap = new EpMap();
				epMap.setStreams(List.of(eps));
				info.setEpMap(epMap);
			}
		}

		return info;
	}

	// -------------------------------------------------------------------------
	// PAT / PMT / PCR packet builders
	// -------------------------------------------------------------------------

	private void writePatPacket(OutputStream out, int pmtPid, Map<Integer, Integer> cc, long ats27) throws IOException {
		// PAT payload: pointer(1) + table_id(1) + section_syntax_indicator+length(2)
		// + tsid(2) + version+current(1) + sec(1) + lastsec(1)
		// + program_number(2) + PMT_PID(2) + CRC(4)
		byte[] payload = new byte[12];
		payload[0] = 0x00; // pointer
		payload[1] = 0x00; // table_id = PAT
		payload[2] = (byte) 0xB0;
		payload[3] = 0x0D; // section_length = 13
		payload[4] = 0x00;
		payload[5] = 0x01; // transport_stream_id
		payload[6] = (byte) 0xC1; // version=0, current=1
		payload[7] = 0x00; // section_number
		payload[8] = 0x00; // last_section_number
		payload[9] = 0x00;
		payload[10] = 0x01; // program_number = 1
		payload[11] = (byte) (0xE0 | ((pmtPid >> 8) & 0x1F));
		// PMT PID low byte comes in next — extend array
		byte[] fullPayload = Arrays.copyOf(payload, 17);
		fullPayload[12] = (byte) (pmtPid & 0xFF);
		// CRC (4 bytes, simplified: write zeros — players are tolerant during
		// authoring)
		long crc = crc32(fullPayload, 1, 12);
		fullPayload[13] = (byte) ((crc >> 24) & 0xFF);
		fullPayload[14] = (byte) ((crc >> 16) & 0xFF);
		fullPayload[15] = (byte) ((crc >> 8) & 0xFF);
		fullPayload[16] = (byte) (crc & 0xFF);

		int seqNum = ccNext(cc, 0x0000);
		writeTsPacket(out, 0x0000, true, seqNum, fullPayload, ats27);
	}

	private void writePmtPacket(OutputStream out, List<M2tsDescriptor.StreamEntry> streams, int pcrPid,
			Map<Integer, Integer> cc, long ats27) throws IOException {
		// PMT fixed header: 12 bytes, then ES loop
		ByteArrayOutputStream pmtBuf = new ByteArrayOutputStream();

		// Pointer field (1 byte)
		pmtBuf.write(0x00);

		// table_id=0x02, section_syntax_indicator=1, private_indicator=0, reserved=11,
		// section_length TBD
		// We'll patch section_length after knowing the total size
		pmtBuf.write(0x02); // table_id
		pmtBuf.write(0xB0); // section_syntax_indicator + private bit + reserved (2 bits)
							// + section_length
							// high nibble = 0
		int sectionLenOffset = pmtBuf.size();
		pmtBuf.write(0x00); // section_length low byte (placeholder)
		pmtBuf.write(0x00);
		pmtBuf.write(0x01); // program_number
		pmtBuf.write(0xC1); // version=0, current=1
		pmtBuf.write(0x00); // section_number
		pmtBuf.write(0x00); // last_section_number
		pmtBuf.write(0xE0 | ((pcrPid >> 8) & 0x1F));
		pmtBuf.write(pcrPid & 0xFF);
		pmtBuf.write(0xF0); // program_info_length high nibble (reserved=1111) + high bits
		pmtBuf.write(0x00); // program_info_length = 0

		// ES loop
		for (M2tsDescriptor.StreamEntry s : streams) {
			pmtBuf.write(s.getStreamTypeByte());
			pmtBuf.write(0xE0 | ((s.getPid() >> 8) & 0x1F));
			pmtBuf.write(s.getPid() & 0xFF);
			// ES info with optional language descriptor
			byte[] esInfo = buildEsDescriptors(s);
			pmtBuf.write((esInfo.length >> 8) & 0x0F | 0xF0);
			pmtBuf.write(esInfo.length & 0xFF);
			pmtBuf.write(esInfo);
		}

		// CRC placeholder (4 bytes)
		pmtBuf.write(new byte[4]);

		byte[] pmtBytes = pmtBuf.toByteArray();
		// section_length = bytes from program_number through end of CRC
		// = total length - pointer(1) - table_id(1) - 2-byte section_length field = total
		// - 4
		int sectionLen = pmtBytes.length - 4;
		pmtBytes[sectionLenOffset - 1] = (byte) (0xB0 | ((sectionLen >> 8) & 0x0F));
		pmtBytes[sectionLenOffset] = (byte) (sectionLen & 0xFF);

		// Compute CRC over table_id..last_byte_before_CRC
		long crc = crc32(pmtBytes, 1, pmtBytes.length - 5);
		int crcOff = pmtBytes.length - 4;
		pmtBytes[crcOff] = (byte) ((crc >> 24) & 0xFF);
		pmtBytes[crcOff + 1] = (byte) ((crc >> 16) & 0xFF);
		pmtBytes[crcOff + 2] = (byte) ((crc >> 8) & 0xFF);
		pmtBytes[crcOff + 3] = (byte) (crc & 0xFF);

		int seqNum = ccNext(cc, PMT_PID);
		writeTsPacket(out, PMT_PID, true, seqNum, pmtBytes, ats27);
	}

	private byte[] buildEsDescriptors(M2tsDescriptor.StreamEntry s) {
		String lang = s.getLanguage();
		// ISO 639 language descriptor: tag=0x0A, length=4, language(3), audio_type(1)
		int streamTypeByte = s.getStreamTypeByte();
		StreamCodingType type = StreamCodingType.fromByte(streamTypeByte);
		if (type.isVideo()) {
			byte[] desc = new byte[10];
			desc[0] = 0x05;// registration_descriptor tag
			desc[1] = 0x08;// length
			desc[2] = 'H'; // "HDMV"
			desc[3] = 'D';
			desc[4] = 'M';
			desc[5] = 'V';
			desc[6] = -1; // 0xFF is video
			desc[7] = (byte) streamTypeByte;
			desc[8] = (byte) (s.getFrameRateFps() != null && s.getFrameRateFps() == 24 ? 0x62 : 0x61);
			desc[9] = 0x3F;
			return desc;
		}
		else if (type.isDolbyAudio()) {
			// this code mirrors what is parsed in M2tsParser
			byte[] desc = new byte[12];
			desc[0] = 0x05;// registration_descriptor tag
			desc[1] = 0x04;// length of AC-3
			desc[2] = 'A';
			desc[3] = 'C';
			desc[4] = '-';
			desc[5] = '3';
			desc[6] = (byte) 0x81; // 0x81 is Dolby Audio
			desc[7] = 0x04;// size of following audio descriptor
			desc[8] = (byte) streamTypeByte;
			Integer br = s.getBitrateKbps();
			BitrateCode bc = type == StreamCodingType.DOLBY_TRUEHD ? BitrateCode.TRUE_HD
					: (br != null && br > 0 ? BitrateCode.fromBitrate(br) : null);
			if (bc == null) {
				log.warn("Unknown bitrate {} kbps for stream PID {}, cannot set audio_type in descriptor", br,
						s.getPid());
				bc = BitrateCode.AC3_192kbps;
			}
			desc[9] = (byte) bc.getCode();
			Integer channels = s.getChannels();
			desc[10] = (byte) (channels != null && channels == 8 ? 0x0E : 0x04); // default
																					// to
																					// stereo
			desc[11] = 0;
			return desc;
		}
		else if (type.isMenu() || type.isSubtitle()) {
			// HDMV registration descriptor for IG menus (0x91) and PG subtitles (0x90)
			byte[] desc = new byte[10];
			desc[0] = 0x05; // registration_descriptor tag
			desc[1] = 0x08; // length = 8
			desc[2] = 'H';
			desc[3] = 'D';
			desc[4] = 'M';
			desc[5] = 'V';
			desc[6] = (byte) 0xFF; // non-video stream marker
			desc[7] = (byte) streamTypeByte;
			desc[8] = 0x00;
			desc[9] = (byte) 0x3F;
			return desc;
		}
		if (lang != null && !lang.isBlank()) {
			byte[] desc = new byte[6];
			desc[0] = 0x0A;
			desc[1] = 0x04;
			String padded = String.format("%-3s", lang).substring(0, 3);
			desc[2] = (byte) padded.charAt(0);
			desc[3] = (byte) padded.charAt(1);
			desc[4] = (byte) padded.charAt(2);
			return desc;
		}
		return new byte[0];
	}

	private void writePcrPacket(OutputStream out, int pid, long pcr27, Map<Integer, Integer> cc, long ats27)
			throws IOException {
		// Adaptation-field-only TS packet carrying PCR
		byte[] af = new byte[184]; // adaptation field fills the rest of the 188-byte TS
									// packet
		af[0] = (byte) (af.length - 1); // adaptation_field_length = 183
		af[1] = (byte) 0x10; // PCR_flag set
		// PCR: 6 bytes = base(33) + reserved(6) + ext(9)
		long pcrBase = pcr27 / 300;
		long pcrExt = pcr27 % 300;
		af[2] = (byte) ((pcrBase >> 25) & 0xFF);
		af[3] = (byte) ((pcrBase >> 17) & 0xFF);
		af[4] = (byte) ((pcrBase >> 9) & 0xFF);
		af[5] = (byte) ((pcrBase >> 1) & 0xFF);
		af[6] = (byte) (((pcrBase & 0x01) << 7) | 0x7E | ((pcrExt >> 8) & 0x01));
		af[7] = (byte) (pcrExt & 0xFF);
		// rest of af is padding (0xFF)
		Arrays.fill(af, 8, af.length, (byte) 0xFF);

		// TS header: sync + 3 header bytes
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((pid >> 8) & 0x1F);
		ts[2] = (byte) (pid & 0xFF);
		// Per MPEG-2 TS spec §2.4.3.3, CC shall not be incremented when
		// adaptation_field_control is '10' (adaptation field only, no payload).
		int ccVal = cc.getOrDefault(pid, 0);
		ts[3] = (byte) (0x20 | (ccVal & 0x0F)); // adaptation only = 10b
		System.arraycopy(af, 0, ts, 4, af.length);

		writeSourcePacket(out, ts, ats27);
	}

	// -------------------------------------------------------------------------
	// PES packet builder
	// -------------------------------------------------------------------------

	/**
	 * Builds a PES packet for one access unit / segment.
	 *
	 * <p>
	 * For {@code stream_id=0xBD} (private_stream_1), the Blu-ray / HDMV specification
	 * requires the first byte of the PES payload to be a <em>sub-stream identifier</em>
	 * so that the decoder can distinguish IGS, PGS, AC-3, DTS, LPCM, etc. carried on the
	 * same logical PES stream_id. The mapping is:
	 * <ul>
	 * <li>0x20–0x3F → Interactive Graphics (IG) sub-stream</li>
	 * <li>0x00–0x1F → Presentation Graphics (PG) sub-stream</li>
	 * <li>0x80–0xBF → private audio (AC-3, DTS, LPCM, …)</li>
	 * </ul>
	 * Without this byte the player cannot identify the private sub-stream type.
	 *
	 * <p>
	 * For video ({@code stream_id=0xE0}) the PES packet_length is set to 0 (unbounded) as
	 * required by the MPEG-2 TS spec for video elementary streams. For non-video streams
	 * the length must fit in 16 bits; callers are responsible for keeping individual
	 * segments small enough (per-segment PES).
	 * @param pts90 presentation time stamp in 90 kHz ticks
	 * @param dts90 decode time stamp in 90 kHz ticks, or {@code null} when equal to PTS
	 * (PTS-only PES header, {@code PTS_DTS_flags=10}). Must be non-null (and ≤ pts90) for
	 * H.264/AVC access units that contain B-frames so that the decoder HRD buffer model
	 * works correctly ({@code PTS_DTS_flags=11}).
	 */
	private byte[] buildPesPacket(int streamId, int substreamId, long pts90, Long dts90, byte[] payload) {
		// PES header: start code (3) + stream_id (1) + length (2) + flags (2) +
		// header_data_length (1) + PTS (5) [+ DTS (5) when dts90 != null]
		boolean hasDts = dts90 != null;
		int headerLen = hasDts ? 19 : 14;
		// For stream_id 0xBD, prepend 1-byte sub-stream id inside the payload.
		boolean needSubstreamId = (streamId == 0xBD);
		int pesDataLen = needSubstreamId ? 1 + payload.length : payload.length;
		byte[] pes = new byte[headerLen + pesDataLen];
		// start code prefix
		pes[0] = 0x00;
		pes[1] = 0x00;
		pes[2] = 0x01;
		pes[3] = (byte) streamId;
		int pesLen = pes.length - 6; // packet_length excludes first 6 bytes; 0 =
										// unbounded for video
		if (streamId == 0xE0)
			pesLen = 0; // video: unbounded
		pes[4] = (byte) ((pesLen >> 8) & 0xFF);
		pes[5] = (byte) (pesLen & 0xFF);
		pes[6] = (byte) 0x80; // marker bits
		if (hasDts) {
			pes[7] = (byte) 0xC0; // PTS_DTS_flags = 11 (PTS and DTS)
			pes[8] = 0x0A; // PES_header_data_length = 10 (5 PTS + 5 DTS)
			// PTS with '0011' 4-bit prefix (ISO 13818-1 Table 2-21)
			pes[9] = (byte) (0x31 | ((pts90 >> 29) & 0x0E));
			pes[10] = (byte) ((pts90 >> 22) & 0xFF);
			pes[11] = (byte) (0x01 | ((pts90 >> 14) & 0xFE));
			pes[12] = (byte) ((pts90 >> 7) & 0xFF);
			pes[13] = (byte) (0x01 | ((pts90 << 1) & 0xFE));
			// DTS with '0001' 4-bit prefix
			pes[14] = (byte) (0x11 | ((dts90 >> 29) & 0x0E));
			pes[15] = (byte) ((dts90 >> 22) & 0xFF);
			pes[16] = (byte) (0x01 | ((dts90 >> 14) & 0xFE));
			pes[17] = (byte) ((dts90 >> 7) & 0xFF);
			pes[18] = (byte) (0x01 | ((dts90 << 1) & 0xFE));
		}
		else {
			pes[7] = (byte) 0x80; // PTS_DTS_flags = 10 (PTS only)
			pes[8] = 0x05; // PES_header_data_length = 5 (PTS)
			// PTS encoding: 4 bits marker + 33 bit PTS + 1 marker
			pes[9] = (byte) (0x21 | ((pts90 >> 29) & 0x0E));
			pes[10] = (byte) ((pts90 >> 22) & 0xFF);
			pes[11] = (byte) (0x01 | ((pts90 >> 14) & 0xFE));
			pes[12] = (byte) ((pts90 >> 7) & 0xFF);
			pes[13] = (byte) (0x01 | ((pts90 << 1) & 0xFE));
		}
		int dataOffset = headerLen;
		if (needSubstreamId) {
			pes[dataOffset++] = (byte) substreamId;
		}
		System.arraycopy(payload, 0, pes, dataOffset, payload.length);
		return pes;
	}

	/**
	 * Splits {@code pes} into 188-byte TS packets (with adaptation field padding on the
	 * last packet) and writes 192-byte source packets.
	 * @return number of TS packets written
	 */
	private int writePesToTs(OutputStream out, int pid, byte[] pes, Map<Integer, Integer> cc, long startPacketIndex)
			throws IOException {
		int tsCount = 0;
		int offset = 0;
		boolean first = true;

		while (offset < pes.length) {
			boolean pusi = first;
			first = false;

			int remaining = pes.length - offset;
			// TS payload capacity: 188 - 4 header = 184 bytes
			int payloadCapacity = TS_PACKET_SIZE - 4;
			int chunkLen = Math.min(remaining, payloadCapacity);

			byte[] ts = new byte[TS_PACKET_SIZE];
			ts[0] = (byte) SYNC_BYTE;
			ts[1] = (byte) ((pusi ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
			ts[2] = (byte) (pid & 0xFF);
			int ccVal = ccNext(cc, pid);
			ts[3] = (byte) (0x10 | (ccVal & 0x0F)); // payload only

			if (chunkLen < payloadCapacity) {
				// Last packet: need adaptation field for stuffing
				int stuffLen = payloadCapacity - chunkLen;
				// adaptation field header: length(1) + flags(1) = 2 bytes min
				// If stuffLen == 1, we put a zero-length adaptation field (the length
				// byte
				// itself consumes 1 byte)
				ts[3] = (byte) (0x30 | (ccVal & 0x0F)); // adaptation + payload
				if (stuffLen == 1) {
					ts[4] = 0x00; // af_length=0 (takes 1 byte)
					// payload at ts[5]
					System.arraycopy(pes, offset, ts, 5, chunkLen);
				}
				else {
					ts[4] = (byte) (stuffLen - 1); // af_length
					ts[5] = 0x00; // flags
					Arrays.fill(ts, 6, 4 + stuffLen, (byte) 0xFF); // stuffing
					System.arraycopy(pes, offset, ts, 4 + stuffLen, chunkLen);
				}
			}
			else {
				System.arraycopy(pes, offset, ts, 4, chunkLen);
			}

			writeSourcePacket(out, ts, atsOf(startPacketIndex + tsCount));
			tsCount++;
			offset += chunkLen;
		}
		return tsCount;
	}

	// -------------------------------------------------------------------------
	// Source packet output
	// -------------------------------------------------------------------------

	private void writeSourcePacket(OutputStream out, byte[] tsPacket, long ats27) throws IOException {
		// TP_extra_header: 30-bit ATS in bits 31..2, 2 copy-permission bits in bits
		// 1..0
		long ats30 = ats27 & 0x3FFFFFFFL;
		out.write((int) ((ats30 >> 22) & 0xFF));
		out.write((int) ((ats30 >> 14) & 0xFF));
		out.write((int) ((ats30 >> 6) & 0xFF));
		out.write((int) ((ats30 << 2) & 0xFF)); // copy-permission = 00 (copy-free)
		out.write(tsPacket);
	}

	// -------------------------------------------------------------------------
	// ATS and CBR helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the 27 MHz ATS (Arrival Time Stamp) for the n-th source packet at the
	 * configured target bitrate. Uses split-quotient arithmetic to avoid 64-bit overflow
	 * over long (multi-hour) streams. The result is not masked to 30 bits here;
	 * {@link #writeSourcePacket} applies the mask on output.
	 */
	private long atsOf(long packetIndex) {
		long q = ATS_NUMERATOR / targetBitrateBps;
		long r = ATS_NUMERATOR % targetBitrateBps;
		return packetIndex * q + (packetIndex * r) / targetBitrateBps;
	}

	/**
	 * Writes a null (stuffing) source packet with PID {@code 0x1FFF}. Null packets carry
	 * no payload and are used to maintain CBR delivery. The continuity counter is not
	 * incremented for PID 0x1FFF (MPEG-2 TS §2.4.3.3).
	 */
	private void writeNullPacket(OutputStream out, long ats27) throws IOException {
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((NULL_PID >> 8) & 0x1F); // PID = 0x1FFF (null PID)
		ts[2] = (byte) (NULL_PID & 0xFF);
		ts[3] = (byte) 0x10; // adaptation_field_control = 01 (payload only), CC = 0
		Arrays.fill(ts, 4, TS_PACKET_SIZE, (byte) 0xFF);
		writeSourcePacket(out, ts, ats27);
	}

	private void writeTsPacket(OutputStream out, int pid, boolean pusi, int ccVal, byte[] payload, long ats27)
			throws IOException {
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((pusi ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
		ts[2] = (byte) (pid & 0xFF);
		ts[3] = (byte) (0x10 | (ccVal & 0x0F)); // payload only

		int copy = Math.min(payload.length, TS_PACKET_SIZE - 4);
		System.arraycopy(payload, 0, ts, 4, copy);
		// Pad remainder with 0xFF
		Arrays.fill(ts, 4 + copy, TS_PACKET_SIZE, (byte) 0xFF);

		writeSourcePacket(out, ts, ats27);
	}

	// -------------------------------------------------------------------------
	// Continuity counter
	// -------------------------------------------------------------------------

	private int ccNext(Map<Integer, Integer> cc, int pid) {
		int val = cc.getOrDefault(pid, 0);
		cc.put(pid, (val + 1) & 0x0F);
		return val;
	}

	// -------------------------------------------------------------------------
	// CRC-32 for MPEG-2 PSI tables
	// -------------------------------------------------------------------------

	private static final int[] CRC_TABLE;
	static {
		CRC_TABLE = new int[256];
		for (int i = 0; i < 256; i++) {
			int crc = i << 24;
			for (int j = 0; j < 8; j++) {
				crc = (crc << 1) ^ ((crc < 0) ? 0x04C11DB7 : 0);
			}
			CRC_TABLE[i] = crc;
		}
	}

	private long crc32(byte[] data, int offset, int length) {
		int crc = 0xFFFFFFFF;
		for (int i = offset; i < offset + length; i++) {
			crc = (crc << 8) ^ CRC_TABLE[((crc >> 24) ^ (data[i] & 0xFF)) & 0xFF];
		}
		return crc & 0xFFFFFFFFL;
	}

	// =========================================================================
	// H.264 bitstream helpers (B-frame PTS/DTS reordering)
	// =========================================================================

	/**
	 * Removes H.264 RBSP emulation-prevention bytes ({@code 0x00 0x00 0x03} sequences
	 * where the {@code 0x03} is discarded) from a NAL body slice.
	 * @param data source byte array
	 * @param offset first byte of the NAL body (i.e. the byte after the NAL header byte)
	 * @param length number of bytes to process
	 * @return RBSP bytes (emulation-prevention bytes removed)
	 */
	private static byte[] removeEmulationPrevention(byte[] data, int offset, int length) {
		byte[] out = new byte[length];
		int outPos = 0;
		int zeros = 0;
		for (int i = 0; i < length; i++) {
			int b = data[offset + i] & 0xFF;
			if (zeros >= 2 && b == 0x03) {
				zeros = 0;
				continue;
			} // drop EP byte
			zeros = (b == 0x00) ? zeros + 1 : 0;
			out[outPos++] = (byte) b;
		}
		return Arrays.copyOf(out, outPos);
	}

	/**
	 * Parses an H.264 SPS NAL unit body and extracts the fields needed for B-frame
	 * PTS/DTS reordering.
	 * @param data ES byte array
	 * @param nalStart offset of the NAL <em>header</em> byte (type field)
	 * @param nalLength length of the NAL unit including the header byte
	 * @return parsed {@link H264SpsInfo}, or {@code null} on parse error
	 */
	static H264SpsInfo parseH264Sps(byte[] data, int nalStart, int nalLength) {
		if (nalLength < 2)
			return null;
		// Strip emulation-prevention from the RBSP body (skip NAL header byte)
		byte[] rbsp = removeEmulationPrevention(data, nalStart + 1, nalLength - 1);
		BitReader br = new BitReader(rbsp);
		H264SpsInfo sps = new H264SpsInfo();
		try {
			int profileIdc = br.readBits(8);
			br.readBits(8); // constraint_set_flags + reserved
			br.readBits(8); // level_idc
			br.readUE(); // seq_parameter_set_id

			boolean hasChroma = (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
					|| profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
					|| profileIdc == 128 || profileIdc == 138 || profileIdc == 139 || profileIdc == 134
					|| profileIdc == 135);
			if (hasChroma) {
				int chromaFmtIdc = br.readUE();
				if (chromaFmtIdc == 3)
					sps.separateColourPlane = br.readBit() != 0;
				br.readUE();
				br.readUE(); // bit_depth_luma/chroma_minus8
				br.readBit(); // qpprime_y_zero_transform_bypass_flag
				if (br.readBit() != 0) { // seq_scaling_matrix_present_flag
					int count = (chromaFmtIdc == 3) ? 12 : 8;
					for (int i = 0; i < count; i++) {
						if (br.readBit() != 0) { // seq_scaling_list_present_flag[i]
							int listSize = (i < 6) ? 16 : 64;
							for (int j = 0; j < listSize; j++)
								br.readSE();
						}
					}
				}
			}

			sps.log2MaxFrameNumMinus4 = br.readUE();
			sps.picOrderCntType = br.readUE();
			if (sps.picOrderCntType == 0) {
				sps.log2MaxPicOrderCntLsbMinus4 = br.readUE();
			}
			else if (sps.picOrderCntType == 1) {
				br.readBit(); // delta_pic_order_always_zero_flag
				br.readSE();
				br.readSE(); // offsets
				int numRef = br.readUE();
				for (int i = 0; i < numRef; i++)
					br.readSE();
			}
			br.readUE();
			br.readBit(); // max_num_ref_frames, gaps_in_frame_num
			br.readUE();
			br.readUE(); // pic_width/height_in_mbs/map_units_minus1
			sps.frameMbsOnly = br.readBit() != 0;
			if (!sps.frameMbsOnly)
				br.readBit(); // mb_adaptive_frame_field_flag
			br.readBit(); // direct_8x8_inference_flag
			if (br.readBit() != 0) {
				br.readUE();
				br.readUE();
				br.readUE();
				br.readUE();
			} // frame_crop
			if (br.readBit() != 0) { // vui_parameters_present_flag
				if (br.readBit() != 0) { // aspect_ratio_info_present_flag
					if (br.readBits(8) == 255) {
						br.readBits(16);
						br.readBits(16);
					}
				}
				if (br.readBit() != 0)
					br.readBit(); // overscan_info
				if (br.readBit() != 0) { // video_signal_type
					br.readBits(3);
					br.readBit();
					if (br.readBit() != 0) {
						br.readBits(8);
						br.readBits(8);
						br.readBits(8);
					}
				}
				if (br.readBit() != 0) {
					br.readUE();
					br.readUE();
				} // chroma_loc_info
				if (br.readBit() != 0) {
					br.readBits(32);
					br.readBits(32);
					br.readBit();
				} // timing_info
				boolean nalHrd = br.readBit() != 0;
				if (nalHrd)
					skipHrdParameters(br);
				boolean vclHrd = br.readBit() != 0;
				if (vclHrd)
					skipHrdParameters(br);
				if (nalHrd || vclHrd)
					br.readBit(); // low_delay_hrd_flag
				br.readBit(); // pic_struct_present_flag
				if (br.readBit() != 0) { // bitstream_restriction_flag
					br.readBit(); // motion_vectors_over_pic_boundaries_flag
					br.readUE();
					br.readUE();
					br.readUE();
					br.readUE(); // byte/bit/mv limits
					sps.numReorderFrames = br.readUE();
					br.readUE(); // max_dec_frame_buffering
				}
			}
		}
		catch (Exception e) {
			return null; // malformed SPS — fall back to no reordering
		}
		return sps;
	}

	/** Skips a {@code hrd_parameters()} block inside VUI (H.264 spec §E.1.2). */
	private static void skipHrdParameters(BitReader br) {
		int cpbCntMinus1 = br.readUE();
		br.readBits(4);
		br.readBits(4); // bit_rate_scale, cpb_size_scale
		for (int i = 0; i <= cpbCntMinus1; i++) {
			br.readUE();
			br.readUE();
			br.readBit();
		}
		br.readBits(5);
		br.readBits(5);
		br.readBits(5);
		br.readBits(5); // delay lengths
	}

	/**
	 * Reads {@code pic_order_cnt_lsb} from the beginning of an H.264 VCL slice NAL unit
	 * (poc_type&nbsp;==&nbsp;0 only).
	 * @param data ES byte array
	 * @param nalStart offset of the NAL header byte
	 * @param nalLength length of the NAL including the header byte
	 * @param sps previously parsed SPS
	 * @return {@code pic_order_cnt_lsb}, or {@code -1} on parse error / wrong poc_type
	 */
	static int readSlicePocLsb(byte[] data, int nalStart, int nalLength, H264SpsInfo sps) {
		if (sps.picOrderCntType != 0 || nalLength < 2)
			return -1;
		byte[] rbsp = removeEmulationPrevention(data, nalStart + 1, nalLength - 1);
		BitReader br = new BitReader(rbsp);
		try {
			br.readUE(); // first_mb_in_slice
			br.readUE(); // slice_type
			br.readUE(); // pic_parameter_set_id
			if (sps.separateColourPlane)
				br.readBits(2);
			br.readBits(sps.log2MaxFrameNumMinus4 + 4); // frame_num
			if (!sps.frameMbsOnly) {
				if (br.readBit() != 0)
					br.readBit(); // field_pic_flag, bottom_field_flag
			}
			int nalType = data[nalStart] & 0x1F;
			if (nalType == 5)
				br.readUE(); // idr_pic_id (IDR slices only)
			return br.readBits(sps.log2MaxPicOrderCntLsbMinus4 + 4); // pic_order_cnt_lsb
		}
		catch (Exception e) {
			return -1;
		}
	}

	// -------------------------------------------------------------------------
	// Inner class: ESReader
	// -------------------------------------------------------------------------

}
