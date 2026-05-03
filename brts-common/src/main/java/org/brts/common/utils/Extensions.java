package org.brts.common.utils;

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
