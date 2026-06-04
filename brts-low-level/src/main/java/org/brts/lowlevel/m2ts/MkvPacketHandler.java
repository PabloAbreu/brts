package org.brts.lowlevel.m2ts;

import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.mpls.PlayMark;
import org.ebml.MasterElement;
import org.ebml.StringElement;
import org.ebml.UTF8StringElement;
import org.ebml.UnsignedIntegerElement;
import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaDocTypes;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link M2tsPacketHandler} that writes demuxed elementary streams into a Matroska (MKV) container using the jebml
 * library.
 * <p>
 * Each tracked PID is mapped to an MKV track. The handler accumulates PES payload bytes per PID and flushes a complete
 * <em>access unit</em> (video frame / audio frame / subtitle display set) to the MKV writer whenever a new Payload Unit
 * Start is received for the same PID.
 *
 * <h2>Codec mapping</h2>
 * <ul>
 * <li>H.264 / AVC → {@code V_MPEG4/ISO/AVC}</li>
 * <li>H.265 / HEVC → {@code V_MPEGH/ISO/HEVC}</li>
 * <li>MPEG-2 Video → {@code V_MPEG2}</li>
 * <li>AC-3 → {@code A_AC3}</li>
 * <li>E-AC-3 → {@code A_EAC3}</li>
 * <li>TrueHD → {@code A_TRUEHD}</li>
 * <li>DTS → {@code A_DTS}</li>
 * <li>DTS-HD / MA → {@code A_DTS}</li>
 * <li>LPCM → {@code A_PCM/INT/BIG}</li>
 * <li>PGS → {@code S_HDMV/PGS}</li>
 * <li>Text sub → {@code S_HDMV/TEXTST}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * try (MkvPacketHandler handler = new MkvPacketHandler(info, mkvPath, pidFilter)) {
 * 	new M2tsDemuxer().demux(m2tsPath, info, handler, pidFilter);
 * }
 * }</pre>
 */
@Slf4j
public class MkvPacketHandler implements M2tsPacketHandler {

	private final MatroskaFileWriter mkvWriter;

	private final FileDataWriter dataWriter;

	/** PID → MKV track number. */
	private final Map<Integer, Integer> pidToTrackNo = new LinkedHashMap<>();

	/** PID → stream info (for PES header parsing decisions). */
	private final Map<Integer, M2tsStreamInfo> pidToStreamInfo = new LinkedHashMap<>();

	// ---- Access-unit accumulation buffers ----
	/** PID → accumulated payload bytes for the current access unit. */
	private final Map<Integer, ByteArrayAccumulator> pidToAccumulator = new LinkedHashMap<>();

	/** PID → PTS of the current access unit (if parsed from PES header). */
	private final Map<Integer, Long> pidToPts = new LinkedHashMap<>();

	/**
	 * Tracks whether this is the first PES unit for a PID (we have no prior AU to flush).
	 */
	private final Set<Integer> firstUnitSeen = new HashSet<>();

	/** Base PTS (first PTS seen across all PIDs) used as time zero for the MKV. */
	private long basePts = -1;

	/** Maximum timecode (ms) written so far — used for the Segment duration. */
	private long maxTimecodeMs = 0;

	/**
	 * Creates an MKV handler and sets up tracks.
	 *
	 * @param info      stream metadata
	 * @param mkvPath   output MKV file path
	 * @param pidFilter PIDs to include; null/empty = all
	 * @throws IOException on I/O error
	 */
	public MkvPacketHandler(M2tsInfo info, Path mkvPath, Set<Integer> pidFilter) throws IOException {
		Files.createDirectories(mkvPath.getParent());
		this.dataWriter = new FileDataWriter(mkvPath.toString());
		this.mkvWriter = new MatroskaFileWriter(dataWriter);
		mkvWriter.setTimecodeScale(1_000_000); // 1 ms resolution

		int trackNo = 1;
		List<M2tsStreamInfo> streams = info.getStreams() != null ? info.getStreams() : List.of();

		// Pre-scan for video dimensions — PGS subtitle tracks must carry a Video
		// element with the display resolution so decoders know the bitmap coordinate
		// space. Default to 1920×1080 when no video stream is present.
		short videoWidth = 1920, videoHeight = 1080;
		for (M2tsStreamInfo s : streams) {
			if (s.getCodingType() != null && s.getCodingType().isVideo()) {
				Integer w = s.getWidthPixels();
				Integer h = s.getHeightPixels();
				if (w != null && h != null) {
					videoWidth = w.shortValue();
					videoHeight = h.shortValue();
				}
				break;
			}
		}

		for (M2tsStreamInfo s : streams) {
			if (pidFilter != null && !pidFilter.isEmpty() && !pidFilter.contains(s.getPid()))
				continue;
			if (s.getCodingType() == null)
				continue;

			MatroskaFileTrack track = buildTrack(s, trackNo, videoWidth, videoHeight);
			if (track == null) {
				log.warn("Unsupported coding type {} for PID 0x{}, skipping", s.getCodingType(),
						Integer.toHexString(s.getPid()));
				continue;
			}

			mkvWriter.addTrack(track);
			pidToTrackNo.put(s.getPid(), trackNo);
			pidToStreamInfo.put(s.getPid(), s);
			pidToAccumulator.put(s.getPid(), new ByteArrayAccumulator());
			pidToPts.put(s.getPid(), -1L);

			log.info("  MKV track {} ← PID 0x{} [{}] codec={}", trackNo, Integer.toHexString(s.getPid()),
					s.getCodingType(), track.getCodecID());
			trackNo++;
		}
	}

	@Override
	public void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart, long packetIndex,
			long ats) throws IOException {

		if (!pidToTrackNo.containsKey(pid))
			return;

		int payloadOff = offset;
		int payloadLen = length;
		long pts = -1;

		// Parse PES header on payload-unit-start packets
		if (payloadUnitStart && payloadLen > 8 && payload[payloadOff] == 0x00 && payload[payloadOff + 1] == 0x00
				&& payload[payloadOff + 2] == 0x01) {

			// Extract PTS if present (PTS_DTS_flags in bits 7-6 of byte 7)
			int flags = (payload[payloadOff + 7] & 0xFF) >> 6;
			if (flags >= 2) { // PTS present
				pts = parsePts(payload, payloadOff + 9);
			}

			int pesHeaderLen = (payload[payloadOff + 8] & 0xFF) + 9;
			payloadOff += pesHeaderLen;
			payloadLen = length - (payloadOff - offset);

			if (payloadLen <= 0)
				return;
		}

		ByteArrayAccumulator acc = pidToAccumulator.get(pid);

		// When we see a new PES unit start, flush the previous access unit
		if (payloadUnitStart && firstUnitSeen.contains(pid)) {
			flushAccessUnit(pid);
		}
		firstUnitSeen.add(pid);

		// Record PTS for this access unit
		if (pts >= 0) {
			if (basePts < 0)
				basePts = pts;
			pidToPts.put(pid, pts);
		}

		acc.write(payload, payloadOff, payloadLen);
	}

	/**
	 * Writes Matroska chapter entries derived from the given playlist marks. Must be called after demuxing (so that
	 * {@code basePts} is known) and before {@link #close()}.
	 *
	 * @param marks chapter marks from the MPLS playlist
	 */
	public void writeChapters(List<PlayMark> marks) {
		if (marks == null || marks.isEmpty()) {
			log.info("  No chapter marks to write");
			return;
		}
		if (basePts < 0) {
			log.warn("  Cannot write chapters: no PTS reference available");
			return;
		}

		MasterElement chaptersElem = MatroskaDocTypes.Chapters.getInstance();
		MasterElement editionEntry = MatroskaDocTypes.EditionEntry.getInstance();

		UnsignedIntegerElement editionUid = MatroskaDocTypes.EditionUID.getInstance();
		editionUid.setValue(1);
		editionEntry.addChildElement(editionUid);

		UnsignedIntegerElement editionFlagDefault = MatroskaDocTypes.EditionFlagDefault.getInstance();
		editionFlagDefault.setValue(1);
		editionEntry.addChildElement(editionFlagDefault);

		for (int i = 0; i < marks.size(); i++) {
			PlayMark mark = marks.get(i);
			// Convert 90 kHz PTS to nanoseconds relative to MKV start
			long chapterTimeNs = (mark.getMarkTimeTicks() - basePts) * 1_000_000L / 90;
			if (chapterTimeNs < 0)
				chapterTimeNs = 0;

			MasterElement chapterAtom = MatroskaDocTypes.ChapterAtom.getInstance();

			UnsignedIntegerElement chapterUid = MatroskaDocTypes.ChapterUID.getInstance();
			chapterUid.setValue(i + 1);
			chapterAtom.addChildElement(chapterUid);

			UnsignedIntegerElement chapterTimeStart = MatroskaDocTypes.ChapterTimeStart.getInstance();
			chapterTimeStart.setValue(chapterTimeNs);
			chapterAtom.addChildElement(chapterTimeStart);

			// Chapter display name
			MasterElement chapterDisplay = MatroskaDocTypes.ChapterDisplay.getInstance();
			UTF8StringElement chapString = MatroskaDocTypes.ChapString.getInstance();
			chapString.setValue(String.format("Chapter %02d", i + 1));
			chapterDisplay.addChildElement(chapString);
			StringElement chapLang = MatroskaDocTypes.ChapLanguage.getInstance();
			chapLang.setValue("eng");
			chapterDisplay.addChildElement(chapLang);
			chapterAtom.addChildElement(chapterDisplay);

			editionEntry.addChildElement(chapterAtom);

			long ms = chapterTimeNs / 1_000_000;
			log.info("    Chapter {}: {}", i + 1, String.format("%d:%02d:%02d.%03d", ms / 3_600_000, (ms / 60_000) % 60,
					(ms / 1_000) % 60, ms % 1_000));
		}

		chaptersElem.addChildElement(editionEntry);
		mkvWriter.setChapters(chaptersElem);
		log.info("  {} chapter(s) queued for MKV", marks.size());
	}

	@Override
	public void close() throws IOException {
		try {
			// Flush remaining access units
			for (int pid : pidToTrackNo.keySet()) {
				ByteArrayAccumulator acc = pidToAccumulator.get(pid);
				if (acc != null && acc.size() > 0) {
					flushAccessUnit(pid);
				}
			}
			// Set total duration so SegmentInfo contains a Duration element
			mkvWriter.setDuration(maxTimecodeMs);
			mkvWriter.close();
		} finally {
			dataWriter.close();
		}
		log.info("MKV file written ({} tracks, duration {}ms)", pidToTrackNo.size(), maxTimecodeMs);
	}

	// -------------------------------------------------------------------------
	// Internals
	// -------------------------------------------------------------------------

	private void flushAccessUnit(int pid) {
		ByteArrayAccumulator acc = pidToAccumulator.get(pid);
		if (acc.size() == 0)
			return;

		int trackNo = pidToTrackNo.get(pid);
		long pts = pidToPts.getOrDefault(pid, -1L);

		// Convert PTS (90 kHz) to MKV timecode (ms)
		long timecodeMs = 0;
		if (pts >= 0 && basePts >= 0) {
			timecodeMs = (pts - basePts) / 90;
		}
		if (timecodeMs > maxTimecodeMs) {
			maxTimecodeMs = timecodeMs;
		}

		MatroskaFileFrame frame = new MatroskaFileFrame();
		frame.setTrackNo(trackNo);
		frame.setTimecode(timecodeMs);
		frame.setKeyFrame(true); // simplified: mark all as key frames
		frame.setData(ByteBuffer.wrap(acc.toByteArray()));

		mkvWriter.addFrame(frame);
		acc.reset();
	}

	private MatroskaFileTrack buildTrack(M2tsStreamInfo s, int trackNo, short videoWidth, short videoHeight) {
		MatroskaFileTrack track = new MatroskaFileTrack();
		track.setTrackNo(trackNo);
		track.setTrackUID(trackNo);
		track.setFlagDefault(trackNo == 1);
		track.setFlagLacing(false);

		if (s.getLanguage() != null)
			track.setLanguage(s.getLanguage());

		String codecId = codecIdFor(s.getCodingType());
		if (codecId == null)
			return null;

		track.setCodecID(codecId);

		if (s.getCodingType().isVideo()) {
			track.setTrackType(MatroskaFileTrack.TrackType.VIDEO);
			track.setName("Video");

			MatroskaFileTrack.MatroskaVideoTrack vt = new MatroskaFileTrack.MatroskaVideoTrack();
			vt.setPixelWidth(s.getWidthPixels() != null ? s.getWidthPixels().shortValue() : (short) 1920);
			vt.setPixelHeight(s.getHeightPixels() != null ? s.getHeightPixels().shortValue() : (short) 1080);
			track.setVideo(vt);

			if (s.getFrameRateFps() != null && s.getFrameRateFps() > 0) {
				track.setDefaultDuration((long) (1_000_000_000.0 / s.getFrameRateFps()));
			}
		} else if (s.getCodingType().isAudio()) {
			track.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
			track.setName(s.getCodingType().name());

			MatroskaFileTrack.MatroskaAudioTrack at = new MatroskaFileTrack.MatroskaAudioTrack();
			at.setSamplingFrequency(s.getSampleRateHz() != null ? s.getSampleRateHz() : 48000);
			at.setChannels(s.getChannels() != null ? s.getChannels().shortValue() : (short) 2);
			track.setAudio(at);
		} else if (s.getCodingType().isSubtitle()) {
			track.setTrackType(MatroskaFileTrack.TrackType.SUBTITLE);
			track.setName("Subtitle");

			// PGS bitmaps are rendered in the video's coordinate space; the
			// MKV spec requires a Video element with PixelWidth/PixelHeight on
			// the subtitle track so that decoders can determine codec parameters.
			if (s.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS) {
				MatroskaFileTrack.MatroskaVideoTrack vt = new MatroskaFileTrack.MatroskaVideoTrack();
				vt.setPixelWidth(videoWidth);
				vt.setPixelHeight(videoHeight);
				track.setVideo(vt);
			}
		} else {
			return null; // skip menus etc.
		}

		return track;
	}

	private static String codecIdFor(StreamCodingType ct) {
		return switch (ct) {
		case H264_AVC -> "V_MPEG4/ISO/AVC";
		case H265_HEVC -> "V_MPEGH/ISO/HEVC";
		case MPEG2_VIDEO -> "V_MPEG2";
		case VC1 -> "V_MS/VFW/FOURCC";
		case DOLBY_AC3 -> "A_AC3";
		case DOLBY_AC3_PLUS -> "A_EAC3";
		case DOLBY_TRUEHD -> "A_TRUEHD";
		case DTS -> "A_DTS";
		case DTS_HD, DTS_HD_MASTER_AUDIO -> "A_DTS";
		case LPCM -> "A_PCM/INT/BIG";
		case PRESENTATION_GRAPHICS -> "S_HDMV/PGS";
		case TEXT_SUBTITLE -> "S_HDMV/TEXTST";
		case INTERACTIVE_GRAPHICS -> null; // menus not supported
		};
	}

	private static long parsePts(byte[] buf, int off) {
		long pts = 0;
		pts |= ((long) (buf[off] & 0x0E)) << 29;
		pts |= ((long) (buf[off + 1] & 0xFF)) << 22;
		pts |= ((long) (buf[off + 2] & 0xFE)) << 14;
		pts |= ((long) (buf[off + 3] & 0xFF)) << 7;
		pts |= ((long) (buf[off + 4] & 0xFE)) >> 1;
		return pts;
	}

	// -------------------------------------------------------------------------
	// Simple growable byte buffer
	// -------------------------------------------------------------------------

	static final class ByteArrayAccumulator {

		private byte[] buf = new byte[256 * 1024]; // 256 KB initial

		private int pos = 0;

		void write(byte[] src, int offset, int length) {
			ensureCapacity(pos + length);
			System.arraycopy(src, offset, buf, pos, length);
			pos += length;
		}

		byte[] toByteArray() {
			return Arrays.copyOf(buf, pos);
		}

		int size() {
			return pos;
		}

		void reset() {
			pos = 0;
		}

		private void ensureCapacity(int needed) {
			if (needed > buf.length) {
				buf = Arrays.copyOf(buf, Math.max(buf.length * 2, needed));
			}
		}

	}

}
