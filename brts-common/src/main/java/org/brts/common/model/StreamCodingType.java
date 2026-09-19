package org.brts.common.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/model/StreamCodingType.java' is part of BRTS.
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

/**
 * Codec types found on a Blu-ray stream. Each constant maps to the Blu-ray stream_coding_type byte value.
 */
public enum StreamCodingType {

	// Video
	// maybe 1 = MPEG-1 ?
	MPEG2_VIDEO(0x02),
	H264_AVC(0x1B),
	H265_HEVC(0x24),
	VC1(0xEA),

	// Audio
	// maybe add 0x03, 0x04, 0xA1, 0XA2
	LPCM(0x80),
	DOLBY_AC3(0x81),
	DTS(0x82),
	DOLBY_TRUEHD(0x83),
	DOLBY_AC3_PLUS(0x84),
	DTS_HD(0x85),
	DTS_HD_MASTER_AUDIO(0x86),
	DTS_EXPRESS(0x87), // a.k.a. DTS-HD Express, a.k.a. DTS-HD LBR

	// Presentation Graphics (subtitles)
	PRESENTATION_GRAPHICS(0x90),

	// Interactive Graphics (menus)
	INTERACTIVE_GRAPHICS(0x91),

	// Text subtitles
	TEXT_SUBTITLE(0x92);

	private final int codingTypeByte;

	StreamCodingType(int codingTypeByte) {
		this.codingTypeByte = codingTypeByte;
	}

	public int getCodingTypeByte() {
		return codingTypeByte;
	}

	public static StreamCodingType fromByte(int b) {
		for (StreamCodingType t : values()) {
			if (t.codingTypeByte == b)
				return t;
		}
		throw new IllegalArgumentException("Unknown stream coding type byte: 0x" + Integer.toHexString(b));
	}

	public boolean isVideo() {
		return this == MPEG2_VIDEO || this == H264_AVC || this == H265_HEVC || this == VC1;
	}

	public boolean isAudio() {
		return this == LPCM || this == DOLBY_AC3 || this == DTS || this == DOLBY_TRUEHD || this == DOLBY_AC3_PLUS
				|| this == DTS_HD || this == DTS_HD_MASTER_AUDIO;
	}

	public boolean isDolbyAudio() {
		return this == DOLBY_AC3 || this == DOLBY_TRUEHD || this == DOLBY_AC3_PLUS;
	}

	public boolean isSubtitle() {
		return this == PRESENTATION_GRAPHICS || this == TEXT_SUBTITLE;
	}

	public boolean isMenu() {
		return this == INTERACTIVE_GRAPHICS;
	}

}
