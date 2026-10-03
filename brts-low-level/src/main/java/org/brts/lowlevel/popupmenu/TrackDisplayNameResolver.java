package org.brts.lowlevel.popupmenu;



import java.util.Locale;
import java.util.Map;

import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.template.TemplateRenderer;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.BrtsI18NLabels;

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
	static final String TEMPLATE_PROPERTY = "popupMenu.trackDisplayName.template";
	static final String DEFAULT_TEMPLATE = "${language} - ${codec}<#if channels?has_content> ${channels}</#if>";
	private static final String UNNAMED_TRACK = "unnamed";

	private TrackDisplayNameResolver() {
	}

	/**
	 * Resolves the display name for a track.
	 *
	 * @param track the source track metadata
	 * @return a human-readable display name, never null
	 */
	public static String resolve(SourceMediaInfo.SourceTrack track) {
		if (hasMeaningfulTrackName(track.getTrackName())) {
			return track.getTrackName();
		}
		return synthesize(track);
	}

	private static boolean hasMeaningfulTrackName(String trackName) {
		return trackName != null && !trackName.isBlank() && !UNNAMED_TRACK.equalsIgnoreCase(trackName.trim());
	}

	private static String synthesize(SourceMediaInfo.SourceTrack track) {
		String template = BrtsFileConfig.getInstance().propertyOrDefault(TEMPLATE_PROPERTY, DEFAULT_TEMPLATE);
		return synthesize(track, template);
	}

	static String synthesize(SourceMediaInfo.SourceTrack track, String template) {
		StreamCodingType ct = track.getCodingType();
		String codec = ct != null ? codecDisplayName(ct) : "";
		String channels = ct != null && ct.isAudio() && track.getChannels() != null
				? AudioChannelLayoutConverter.channelsDisplayName(track.getChannels())
				: "";
		Map<String, Object> model = Map.of("language", languageDisplayName(track), "codec", codec, "channels", channels,
				"trackNumber", track.getTrackNumber());
		return TemplateRenderer.render(template, model);
	}

	private static String languageDisplayName(SourceMediaInfo.SourceTrack track) {
		String languageCode = track.getLanguage();
		if (languageCode == null || languageCode.isBlank() || "und".equalsIgnoreCase(languageCode.trim())) {
			return "Track " + track.getTrackNumber();
		}
		String normalizedCode = languageCode.trim().toLowerCase(Locale.ROOT);
		return BrtsI18NLabels.getLanguageName(normalizedCode, capitalizeLanguage(normalizedCode));
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
		case DTS_EXPRESS -> "DTS-HD Express";
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
