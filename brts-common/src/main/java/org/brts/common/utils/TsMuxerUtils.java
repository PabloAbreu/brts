package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/TsMuxerUtils.java' is part of BRTS.
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

import java.io.File;

import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TsMuxerUtils {
	public static final String TSMUXER_BINARY = "tsmuxer.binary";

	public static String resolveTsMuxeRBinary() {
		String error = "";
		String tsmuxer = BrtsFileConfig.getInstance().getProperty(TSMUXER_BINARY);
		if (tsmuxer == null) {
			error += "Property 'tsmuxer.binary' is not set. Please set it to the path of the tsMuxeR CLI binary.";
			tsmuxer = ProcessUtils.findFullPath("tsMuxeR");
		}
		if (tsmuxer == null) {
			error += " tsMuxeR binary not found in system PATH either.";
			throw new IllegalStateException(error);
		}
		File tsmuxerFile = new File(tsmuxer);
		if (!tsmuxerFile.exists() || !tsmuxerFile.isFile() || !tsmuxerFile.canExecute()) {
			throw new IllegalStateException(
					"Invalid tsMuxeR binary path: " + tsmuxer + ". Please ensure the file exists and is executable.");
		}
		return tsmuxer;
	}

	public static boolean isTsMuxeRAvailable() {
		try {
			resolveTsMuxeRBinary();
			return true;
		} catch (IllegalStateException e) {
			log.warn("tsMuxeR binary not available: {}", e.getMessage());
			return false;
		}
	}

	public static String translateCodecToTsMuxer(int streamTypeByte) {
		return switch (streamTypeByte) {
		case 0x1B -> "V_MPEG4/ISO/AVC"; // H.264
		case 0x24 -> "V_MPEG4/ISO/HEVC"; // H.265
		case 0x06 -> "A_AC3"; // AC-3
		case 0x81 -> "A_AC3"; // E-AC-3
		case 0x84 -> "A_AC3"; // E-AC-3
		case 0x90 -> "S_HDMV/PGS"; // subs
		default ->
			throw new IllegalArgumentException(String.format("Unsupported stream type byte: 0x%02X", streamTypeByte));
		};
	}

	public static String tsmuxerCodecFor(SourceMediaInfo.SourceTrack track) {
		StreamCodingType codingType = track.getCodingType();
		if (codingType == null) {
			return "A_LPCM";
		}
		return switch (codingType) {
		case H264_AVC -> "V_MPEG4/ISO/AVC";
		case H265_HEVC -> "V_MPEGH/ISO/HEVC";
		case MPEG2_VIDEO -> "V_MPEG-2";
		case VC1 -> "V_MS/VFW/WVC1";
		case DOLBY_AC3 -> "A_AC3";
		case DOLBY_AC3_PLUS -> "A_AC3";
		case DOLBY_TRUEHD -> "A_TRUEHD";
		case DTS, DTS_HD, DTS_HD_MASTER_AUDIO, DTS_EXPRESS -> "A_DTS";
		case LPCM -> "A_LPCM";
		case PRESENTATION_GRAPHICS -> "S_HDMV/PGS";
		case TEXT_SUBTITLE -> tsmuxerTextCodec(track.getSubtitleFormat());
		case INTERACTIVE_GRAPHICS -> "S_HDMV/IGS";
		};
	}

	public static String tsmuxerTextCodec(String subtitleFormat) {
		if (subtitleFormat == null) {
			return "S_TEXT/UTF8";
		}
		return switch (subtitleFormat.toUpperCase()) {
		case "ASS" -> "S_TEXT/ASS";
		case "SSA" -> "S_TEXT/SSA";
		default -> "S_TEXT/UTF8";
		};
	}

}
