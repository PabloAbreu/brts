package org.brts.common.mp4;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/mp4/Mp4SourceMediaParserTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_ASS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_DTS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_EAC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HDMV_PGS_SUBTITLE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG2VIDEO;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MOV_TEXT;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_BLURAY;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S24BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_SUBRIP;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_TEXT;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_TRUEHD;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VC1;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PROFILE_DTS_HD_HRA;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PROFILE_DTS_HD_MA;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PROFILE_UNKNOWN;

import org.brts.common.exception.ParseException;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;

class Mp4SourceMediaParserTest {

	// -------------------------------------------------------------------------
	// mapCodecId — video
	// -------------------------------------------------------------------------

	@Test
	void mapCodecId_h264() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_H264, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.H264_AVC);
	}

	@Test
	void mapCodecId_hevc() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_HEVC, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.H265_HEVC);
	}

	@Test
	void mapCodecId_mpeg2video() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_MPEG2VIDEO, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.MPEG2_VIDEO);
	}

	@Test
	void mapCodecId_vc1() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_VC1, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.VC1);
	}

	// -------------------------------------------------------------------------
	// mapCodecId — audio
	// -------------------------------------------------------------------------

	@Test
	void mapCodecId_ac3() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_AC3, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.DOLBY_AC3);
	}

	@Test
	void mapCodecId_eac3() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_EAC3, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.DOLBY_AC3_PLUS);
	}

	@Test
	void mapCodecId_truehd() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_TRUEHD, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.DOLBY_TRUEHD);
	}

	@Test
	void mapCodecId_dts_plain() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_DTS, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.DTS);
	}

	@Test
	void mapCodecId_dts_hd_ma() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_DTS, AV_PROFILE_DTS_HD_MA))
				.isEqualTo(StreamCodingType.DTS_HD_MASTER_AUDIO);
	}

	@Test
	void mapCodecId_dts_hd_hra() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_DTS, AV_PROFILE_DTS_HD_HRA))
				.isEqualTo(StreamCodingType.DTS_HD);
	}

	@Test
	void mapCodecId_pcm_s16be() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_PCM_S16BE, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.LPCM);
	}

	@Test
	void mapCodecId_pcm_s24be() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_PCM_S24BE, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.LPCM);
	}

	@Test
	void mapCodecId_pcm_bluray() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_PCM_BLURAY, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.LPCM);
	}

	// -------------------------------------------------------------------------
	// mapCodecId — subtitle
	// -------------------------------------------------------------------------

	@Test
	void mapCodecId_pgs() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_HDMV_PGS_SUBTITLE, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.PRESENTATION_GRAPHICS);
	}

	@Test
	void mapCodecId_ass() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_ASS, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.TEXT_SUBTITLE);
	}

	@Test
	void mapCodecId_subrip() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_SUBRIP, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.TEXT_SUBTITLE);
	}

	@Test
	void mapCodecId_movText() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_MOV_TEXT, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.TEXT_SUBTITLE);
	}

	@Test
	void mapCodecId_text() {
		assertThat(Mp4SourceMediaParser.mapCodecId(AV_CODEC_ID_TEXT, AV_PROFILE_UNKNOWN))
				.isEqualTo(StreamCodingType.TEXT_SUBTITLE);
	}

	// -------------------------------------------------------------------------
	// mapCodecId — unknown codec throws ParseException
	// -------------------------------------------------------------------------

	@Test
	void mapCodecId_unknownCodec_throwsParseException() {
		assertThatThrownBy(() -> Mp4SourceMediaParser.mapCodecId(-9999, AV_PROFILE_UNKNOWN))
				.isInstanceOf(ParseException.class).hasMessageContaining("-9999");
	}

	// -------------------------------------------------------------------------
	// deriveSubtitleFormat
	// -------------------------------------------------------------------------

	@Test
	void deriveSubtitleFormat_pgs() {
		assertThat(Mp4SourceMediaParser.deriveSubtitleFormat(AV_CODEC_ID_HDMV_PGS_SUBTITLE)).isEqualTo("PGS");
	}

	@Test
	void deriveSubtitleFormat_ass() {
		assertThat(Mp4SourceMediaParser.deriveSubtitleFormat(AV_CODEC_ID_ASS)).isEqualTo("ASS");
	}

	@Test
	void deriveSubtitleFormat_srt() {
		assertThat(Mp4SourceMediaParser.deriveSubtitleFormat(AV_CODEC_ID_SUBRIP)).isEqualTo("SRT");
	}

	@Test
	void deriveSubtitleFormat_movText() {
		assertThat(Mp4SourceMediaParser.deriveSubtitleFormat(AV_CODEC_ID_MOV_TEXT)).isEqualTo("TX3G");
	}

	@Test
	void deriveSubtitleFormat_unknown() {
		assertThat(Mp4SourceMediaParser.deriveSubtitleFormat(-9999)).isEqualTo("UNKNOWN");
	}

}
