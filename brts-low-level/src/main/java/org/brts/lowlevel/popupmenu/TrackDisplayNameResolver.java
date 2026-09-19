package org.brts.lowlevel.popupmenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/TrackDisplayNameResolver.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

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
