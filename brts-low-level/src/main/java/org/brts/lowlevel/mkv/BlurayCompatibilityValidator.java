package org.brts.lowlevel.mkv;

import org.brts.common.exception.BrtException;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that source media tracks are compatible with the Blu-ray disc format.
 * <p>
 * <b>Video</b> must be one of: MPEG-2, H.264 (AVC), H.265 (HEVC), VC-1. For H.264,
 * profile must be ≤ High (100) and level must be ≤ 4.1.
 * <p>
 * <b>Audio</b> must be one of: LPCM, Dolby AC-3, Dolby TrueHD, Dolby Digital Plus, DTS,
 * DTS-HD, DTS-HD Master Audio.
 * <p>
 * <b>Subtitles</b>: PGS (PRESENTATION_GRAPHICS) passes directly. Text-based subtitles
 * (SRT, ASS, SSA) are flagged as requiring conversion to PGS. Other subtitle formats
 * (e.g. VOBSUB) are rejected.
 */
public class BlurayCompatibilityValidator {

	private static final Logger log = LoggerFactory.getLogger(BlurayCompatibilityValidator.class);

	/** Blu-ray compatible video coding types. */
	private static final Set<StreamCodingType> BD_VIDEO_TYPES = Set.of(StreamCodingType.MPEG2_VIDEO,
			StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC, StreamCodingType.VC1);

	/** Blu-ray compatible audio coding types. */
	private static final Set<StreamCodingType> BD_AUDIO_TYPES = Set.of(StreamCodingType.LPCM,
			StreamCodingType.DOLBY_AC3, StreamCodingType.DTS, StreamCodingType.DOLBY_TRUEHD,
			StreamCodingType.DOLBY_AC3_PLUS, StreamCodingType.DTS_HD, StreamCodingType.DTS_HD_MASTER_AUDIO);

	/** Text subtitle formats that can be converted to PGS. */
	private static final Set<String> CONVERTIBLE_SUB_FORMATS = Set.of("SRT", "ASS", "SSA");

	/** Maximum H.264 profile for Blu-ray: High Profile (100). */
	private static final int H264_MAX_PROFILE = 100;

	/** Maximum H.264 level for Blu-ray: 4.1 (encoded as 41). */
	private static final int H264_MAX_LEVEL = 41;

	/**
	 * Result of validating a single track.
	 */
	public record TrackValidation(int trackNumber, StreamCodingType codingType, boolean compatible,
			boolean needsConversion, String message) {
	}

	/**
	 * Validates all tracks in the source media info and returns per-track results.
	 * @param info the parsed MKV media info
	 * @return list of validation results, one per track
	 */
	public List<TrackValidation> validate(SourceMediaInfo info) {
		List<TrackValidation> results = new ArrayList<>();
		for (SourceMediaInfo.SourceTrack track : info.getTracks()) {
			results.add(validateTrack(track));
		}
		return results;
	}

	/**
	 * Validates all tracks and throws a {@link BrtException} if any track that is not
	 * convertible fails validation.
	 * @param info the parsed MKV media info
	 * @return list of validation results (all passing or convertible)
	 * @throws BrtException if a non-convertible track is incompatible
	 */
	public List<TrackValidation> validateOrThrow(SourceMediaInfo info) {
		List<TrackValidation> results = validate(info);
		for (TrackValidation tv : results) {
			if (!tv.compatible() && !tv.needsConversion()) {
				throw new BrtException("Blu-ray incompatible track #" + tv.trackNumber() + " (" + tv.codingType()
						+ "): " + tv.message());
			}
		}
		return results;
	}

	private TrackValidation validateTrack(SourceMediaInfo.SourceTrack track) {
		StreamCodingType ct = track.getCodingType();
		int trackNo = track.getTrackNumber();

		if (ct.isVideo()) {
			return validateVideo(track);
		}
		else if (ct.isAudio()) {
			return validateAudio(track);
		}
		else if (ct.isSubtitle() || ct == StreamCodingType.TEXT_SUBTITLE) {
			return validateSubtitle(track);
		}

		return new TrackValidation(trackNo, ct, false, false, "Unknown stream category for coding type: " + ct);
	}

	private TrackValidation validateVideo(SourceMediaInfo.SourceTrack track) {
		StreamCodingType ct = track.getCodingType();
		int trackNo = track.getTrackNumber();

		if (!BD_VIDEO_TYPES.contains(ct)) {
			return new TrackValidation(trackNo, ct, false, false, "Video codec " + ct + " is not Blu-ray compatible. "
					+ "Supported: MPEG-2, H.264 (AVC), H.265 (HEVC), VC-1.");
		}

		// H.264 profile/level check
		if (ct == StreamCodingType.H264_AVC) {
			byte[] codecPrivate = track.getCodecPrivate();
			if (codecPrivate != null && codecPrivate.length >= 4) {
				int profile = codecPrivate[1] & 0xFF;
				int level = codecPrivate[3] & 0xFF;
				log.debug("H.264 track #{}: profile={}, level={}", trackNo, profile, level);

				if (profile > H264_MAX_PROFILE) {
					return new TrackValidation(trackNo, ct, false, false, "H.264 profile " + profile
							+ " exceeds Blu-ray maximum (High Profile = " + H264_MAX_PROFILE + ").");
				}
				if (level > H264_MAX_LEVEL) {
					return new TrackValidation(trackNo, ct, false, false,
							"H.264 level " + level + " exceeds Blu-ray maximum (4.1 = " + H264_MAX_LEVEL + ").");
				}
				log.info("H.264 track #{}: profile={}, level={} — Blu-ray compatible", trackNo, profile, level);
			}
			else {
				log.warn("H.264 track #{}: no codec private data available, skipping profile/level check", trackNo);
			}
		}

		return new TrackValidation(trackNo, ct, true, false, "Video OK: " + ct);
	}

	private TrackValidation validateAudio(SourceMediaInfo.SourceTrack track) {
		StreamCodingType ct = track.getCodingType();
		int trackNo = track.getTrackNumber();

		if (!BD_AUDIO_TYPES.contains(ct)) {
			return new TrackValidation(trackNo, ct, false, false, "Audio codec " + ct + " is not Blu-ray compatible. "
					+ "Supported: LPCM, AC-3, E-AC3, TrueHD, DTS, DTS-HD, DTS-HD MA.");
		}

		return new TrackValidation(trackNo, ct, true, false, "Audio OK: " + ct);
	}

	private TrackValidation validateSubtitle(SourceMediaInfo.SourceTrack track) {
		StreamCodingType ct = track.getCodingType();
		int trackNo = track.getTrackNumber();

		if (ct == StreamCodingType.PRESENTATION_GRAPHICS) {
			return new TrackValidation(trackNo, ct, true, false, "PGS subtitle — passes through");
		}

		if (ct == StreamCodingType.TEXT_SUBTITLE) {
			String fmt = track.getSubtitleFormat();
			if (fmt != null && CONVERTIBLE_SUB_FORMATS.contains(fmt.toUpperCase())) {
				log.info("Text subtitle track #{} ({}) will be converted to PGS", trackNo, fmt);
				return new TrackValidation(trackNo, ct, false, true,
						"Text subtitle (" + fmt + ") will be converted to PGS");
			}
			return new TrackValidation(trackNo, ct, false, false,
					"Text subtitle format '" + fmt + "' cannot be converted to PGS");
		}

		return new TrackValidation(trackNo, ct, false, false,
				"Subtitle type " + ct + " is not Blu-ray compatible and cannot be converted");
	}

}
