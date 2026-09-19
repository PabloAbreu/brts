package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/Extensions.java' is part of BRTS.
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

import org.brts.common.m2ts.model.M2tsStreamInfo;

public class Extensions {
	public static String extensionForStream(M2tsStreamInfo s) {
		if (s.getCodingType() == null) {
			return "bin";
		}
		return switch (s.getCodingType()) {
		case LPCM -> "lpcm";
		case DOLBY_AC3 -> "ac3";
		case DOLBY_AC3_PLUS -> "eac3";
		case DOLBY_TRUEHD -> "thd";
		case DTS -> "dts";
		case DTS_HD -> "dtshd";
		case DTS_HD_MASTER_AUDIO -> "dtsma";
		case H264_AVC -> "h264";
		case H265_HEVC -> "hevc";
		case MPEG2_VIDEO -> "m2v";
		case VC1 -> "vc1";
		case PRESENTATION_GRAPHICS -> "pgs";
		case INTERACTIVE_GRAPHICS -> "igs";
		case TEXT_SUBTITLE -> "txt";
		default -> "bin";
		};
	}
}
