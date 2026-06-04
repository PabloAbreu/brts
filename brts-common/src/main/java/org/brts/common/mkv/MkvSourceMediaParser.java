package org.brts.common.mkv;

import org.brts.common.exception.ParseException;
import org.brts.common.model.StreamCodingType;
import org.ebml.io.FileDataSource;
import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileTrack.MatroskaAudioTrack;
import org.ebml.matroska.MatroskaFileTrack.MatroskaVideoTrack;
import org.ebml.matroska.MatroskaFileTrack.TrackType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * MKV (Matroska) implementation of {@link SourceMediaParser} using the jebml library.
 * <p>
 * Extracts track metadata (codec, resolution, frame rate, audio channels, sample rate, language) from the MKV Tracks
 * element without demuxing the frames.
 * <p>
 * Codec mapping from Matroska codec IDs to Blu-ray {@link StreamCodingType}:
 * <ul>
 * <li>V_MPEG4/ISO/AVC → H264_AVC</li>
 * <li>V_MPEGH/ISO/HEVC → H265_HEVC</li>
 * <li>V_MS/VFW/FOURCC (VC-1) → VC1</li>
 * <li>V_MPEG2 → MPEG2_VIDEO</li>
 * <li>A_AC3 → DOLBY_AC3</li>
 * <li>A_EAC3 → DOLBY_AC3_PLUS</li>
 * <li>A_TRUEHD → DOLBY_TRUEHD</li>
 * <li>A_DTS → DTS</li>
 * <li>A_DTS/HD/MA → DTS_HD_MASTER_AUDIO</li>
 * <li>A_DTS/HD/HRA → DTS_HD</li>
 * <li>A_PCM/INT/BIG → LPCM</li>
 * <li>S_HDMV/PGS → PRESENTATION_GRAPHICS</li>
 * <li>S_TEXT/UTF8 → TEXT_SUBTITLE</li>
 * </ul>
 */
@Slf4j
public class MkvSourceMediaParser implements SourceMediaParser {

	@Override
	public String[] supportedExtensions() {
		return new String[] { "mkv", "mka", "mks", "webm" };
	}

	@Override
	public SourceMediaInfo parse(Path path) throws IOException {
		log.info("Parsing MKV: {}", path);

		SourceMediaInfo info = new SourceMediaInfo();
		info.setSourcePath(path.toAbsolutePath().toString());

		try (FileDataSource dataSource = new FileDataSource(path.toAbsolutePath().toString())) {
			MatroskaFile mkv = new MatroskaFile(dataSource);
			// mkv.setScanFirstCluster(false);
			mkv.readFile();

			// Duration: MatroskaFile.getDuration() returns duration in timecode scale
			// units
			// (typically nanoseconds when timecodeScale=1000000, i.e. milliseconds *
			// 1000000 ns)
			// getTimecodeScale() is in nanoseconds per tick, getDuration() is in ticks
			long timecodeScaleNs = mkv.getTimecodeScale(); // nanoseconds per tick
															// (default 1,000,000)
			double durationTicks = mkv.getDuration(); // duration in ticks
			long durationMs = (long) (durationTicks * timecodeScaleNs / 1_000_000.0);
			info.setDurationMs(durationMs);
			log.debug("Duration: {} ticks × {} ns/tick = {} ms", durationTicks, timecodeScaleNs, durationMs);

			List<SourceMediaInfo.SourceTrack> tracks = new ArrayList<>();

			MatroskaFileTrack[] trackList = mkv.getTrackList();
			if (trackList != null) {
				for (MatroskaFileTrack mkvTrack : trackList) {
					SourceMediaInfo.SourceTrack track = new SourceMediaInfo.SourceTrack();
					track.setTrackNumber(mkvTrack.getTrackNo());
					track.setLanguage(mkvTrack.getLanguage());

					String codecId = mkvTrack.getCodecID();
					try {
						track.setCodingType(mapCodecId(codecId));
					} catch (ParseException e) {
						log.warn("Track {}: unsupported codec '{}', skipping", mkvTrack.getTrackNo(), codecId);
						continue;
					}

					// Track name (human-readable label from MKV, e.g. "Director's Commentary")
					if (mkvTrack.getName() != null && !mkvTrack.getName().isBlank()) {
						track.setTrackName(mkvTrack.getName());
					}

					TrackType trackType = mkvTrack.getTrackType();

					// Capture codec private data (e.g. AVCDecoderConfigurationRecord)
					ByteBuffer cpBuf = mkvTrack.getCodecPrivate();
					if (cpBuf != null && cpBuf.remaining() > 0) {
						byte[] cpBytes = new byte[cpBuf.remaining()];
						cpBuf.duplicate().get(cpBytes);
						track.setCodecPrivate(cpBytes);
					}

					if (trackType == TrackType.VIDEO) {
						MatroskaVideoTrack video = mkvTrack.getVideo();
						if (video != null) {
							track.setWidthPixels((int) (video.getPixelWidth() & 0xFFFF));
							track.setHeightPixels((int) (video.getPixelHeight() & 0xFFFF));
						}
						// DefaultDuration is in nanoseconds; convert to fps
						long defaultDurationNs = mkvTrack.getDefaultDuration();
						if (defaultDurationNs > 0) {
							track.setFrameRateFps(1_000_000_000.0 / defaultDurationNs);
						}
						log.debug("Video track {}: codec={}, {}×{}, fps={}", track.getTrackNumber(), codecId,
								track.getWidthPixels(), track.getHeightPixels(), track.getFrameRateFps());

					} else if (trackType == TrackType.AUDIO) {
						MatroskaAudioTrack audio = mkvTrack.getAudio();
						if (audio != null) {
							track.setSampleRateHz((int) audio.getSamplingFrequency());
							track.setChannels((int) audio.getChannels());
							track.setBitrateKbps(audio.getBitrateKbps());
						}
						log.debug("Audio track {}: codec={}, lang={}, {}ch@{}Hz, {} kbps", track.getTrackNumber(),
								codecId, track.getLanguage(), track.getChannels(), track.getSampleRateHz(),
								track.getBitrateKbps());

					} else if (trackType == TrackType.SUBTITLE) {
						// Derive a human-readable subtitle format from the codec ID
						track.setSubtitleFormat(deriveSubtitleFormat(codecId));
						log.debug("Subtitle track {}: codec={}, lang={}, format={}", track.getTrackNumber(), codecId,
								track.getLanguage(), track.getSubtitleFormat());
					}

					tracks.add(track);
				}
			}

			info.setTracks(tracks);
			log.info("Parsed {} tracks from {}", tracks.size(), path.getFileName());
		}

		return info;
	}

	// -------------------------------------------------------------------------
	// Codec mapping
	// -------------------------------------------------------------------------

	static StreamCodingType mapCodecId(String codecId) {
		if (codecId == null)
			throw new ParseException("Null codec ID");
		return switch (codecId) {
		case "V_MPEG4/ISO/AVC" -> StreamCodingType.H264_AVC;
		case "V_MPEGH/ISO/HEVC" -> StreamCodingType.H265_HEVC;
		case "V_MPEG2" -> StreamCodingType.MPEG2_VIDEO;
		case "V_MS/VFW/FOURCC" -> StreamCodingType.VC1;
		case "A_AC3" -> StreamCodingType.DOLBY_AC3;
		case "A_EAC3" -> StreamCodingType.DOLBY_AC3_PLUS;
		case "A_TRUEHD" -> StreamCodingType.DOLBY_TRUEHD;
		case "A_DTS" -> StreamCodingType.DTS;
		case "A_DTS/HD/MA" -> StreamCodingType.DTS_HD_MASTER_AUDIO;
		case "A_DTS/HD/HRA" -> StreamCodingType.DTS_HD;
		case "A_PCM/INT/BIG" -> StreamCodingType.LPCM;
		case "S_HDMV/PGS" -> StreamCodingType.PRESENTATION_GRAPHICS;
		case "S_TEXT/UTF8", "S_TEXT/ASS" -> StreamCodingType.TEXT_SUBTITLE;
		default -> throw new ParseException("Unsupported MKV codec ID: " + codecId);
		};
	}

	/**
	 * Maps a Matroska subtitle codec ID to a short human-readable format string stored in
	 * {@link SourceMediaInfo.SourceTrack#getSubtitleFormat()}.
	 */
	static String deriveSubtitleFormat(String codecId) {
		if (codecId == null)
			return "UNKNOWN";
		return switch (codecId) {
		case "S_HDMV/PGS" -> "PGS";
		case "S_TEXT/UTF8" -> "SRT";
		case "S_TEXT/ASS" -> "ASS";
		case "S_TEXT/SSA" -> "SSA";
		case "S_VOBSUB" -> "VOBSUB";
		default -> codecId;
		};
	}

}
