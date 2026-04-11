package org.brts.common.model;

/**
 * Codec types found on a Blu-ray stream. Each constant maps to the Blu-ray
 * stream_coding_type byte value.
 */
public enum StreamCodingType {

	// Video
	MPEG2_VIDEO(0x02), H264_AVC(0x1B), H265_HEVC(0x24), VC1(0xEA),

	// Audio
	LPCM(0x80), DOLBY_AC3(0x81), DTS(0x82), DOLBY_TRUEHD(0x83), DOLBY_AC3_PLUS(0x84), DTS_HD(0x85),
	DTS_HD_MASTER_AUDIO(0x86),

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
