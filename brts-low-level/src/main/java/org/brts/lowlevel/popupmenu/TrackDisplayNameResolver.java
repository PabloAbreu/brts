package org.brts.lowlevel.popupmenu;

import java.util.Locale;

import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;

/**
 * Resolves a human-readable display name for a track, suitable for popup menu button labels.
 * <p>
 * Resolution strategy:
 * <ol>
 * <li>If the source track has a non-blank {@code trackName}, use it</li>
 * <li>Otherwise, synthesize from language code + codec type + channel layout (for audio)</li>
 * </ol>
 */
public final class TrackDisplayNameResolver {

	private TrackDisplayNameResolver() {
	}

	/**
	 * Resolves the display name for a track.
	 *
	 * @param track the source track metadata
	 * @return a human-readable display name, never null
	 */
	public static String resolve(SourceMediaInfo.SourceTrack track) {
		if (track.getTrackName() != null && !track.getTrackName().isBlank()) {
			return track.getTrackName();
		}
		return synthesize(track);
	}

	private static String synthesize(SourceMediaInfo.SourceTrack track) {
		StringBuilder sb = new StringBuilder();

		// Language
		String lang = track.getLanguage();
		if (lang != null && !lang.isBlank() && !"und".equals(lang)) {
			sb.append(capitalizeLanguage(lang));
		} else {
			sb.append("Track ").append(track.getTrackNumber());
		}

		// Codec name
		StreamCodingType ct = track.getCodingType();
		if (ct != null) {
			sb.append(" - ").append(codecDisplayName(ct));
		}

		// Channel layout for audio
		if (ct != null && ct.isAudio() && track.getChannels() != null) {
			sb.append(" ").append(AudioChannelLayoutConverter.channelsDisplayName(track.getChannels()));
		}

		return sb.toString();
	}

	private static String capitalizeLanguage(String iso639) {
		// Convert ISO 639-2 three-letter code to a capitalized display form
		Locale locale = Locale.forLanguageTag(iso639);
		String display = locale.getDisplayLanguage(Locale.ENGLISH);
		if (display != null && !display.isEmpty() && !display.equals(iso639)) {
			return display;
		}
		// Fallback: capitalize the code itself
		return iso639.substring(0, 1).toUpperCase(Locale.ROOT) + iso639.substring(1);
	}

	private static String codecDisplayName(StreamCodingType ct) {
		return switch (ct) {
		case DOLBY_AC3 -> "AC3";
		case DOLBY_AC3_PLUS -> "E-AC3";
		case DOLBY_TRUEHD -> "TrueHD";
		case DTS -> "DTS";
		case DTS_HD -> "DTS-HD HRA";
		case DTS_HD_MASTER_AUDIO -> "DTS-HD MA";
		case LPCM -> "LPCM";
		case PRESENTATION_GRAPHICS -> "PGS";
		case TEXT_SUBTITLE -> "Text";
		case H264_AVC -> "H.264";
		case H265_HEVC -> "H.265";
		case MPEG2_VIDEO -> "MPEG-2";
		case VC1 -> "VC-1";
		case INTERACTIVE_GRAPHICS -> "IGS";
		};
	}

}
