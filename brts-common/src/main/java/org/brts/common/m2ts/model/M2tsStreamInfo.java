package org.brts.common.m2ts.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/model/M2tsStreamInfo.java' is part of BRTS.
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

import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.model.StreamCodingType;

import lombok.Getter;
import lombok.Setter;

/**
 * Describes a single elementary stream discovered inside an M2TS file.
 * <p>
 * Populated by {@link org.brts.lowlevel.m2ts.M2tsParser} after scanning the PMT and a few seconds of packet headers.
 */
@Getter
@Setter
public class M2tsStreamInfo implements IStreamInfo {

	/** PID in the MPEG-2 transport stream. */
	private int pid;

	/**
	 * ISO 13818-1 stream_type byte from the PMT descriptor. E.g. 0x02=MPEG-2 video, 0x1B=H.264, 0x24=HEVC, 0x80=LPCM,
	 * 0x81=AC3, 0x82=DTS, 0x83=TrueHD, 0x84=E-AC3, 0x85=DTS-HD, 0x86=DTS-MA, 0x90=PGS, 0x92=text subtitle.
	 */
	private int streamTypeByte;

	/**
	 * Mapped Blu-ray coding type — null when the stream_type byte is unrecognised.
	 */
	private StreamCodingType codingType;

	private String registration; // from registration descriptor, e.g. "HDMV"

	/**
	 * ISO 639-2 language tag extracted from the PMT language descriptor (may be null).
	 */
	private String language;

	private Integer bitrateKbps;

	// ---- video-specific (populated on first IDR frame parse) ----

	/** Pixel width (from SPS for H.264/HEVC, or sequence header for MPEG-2). */
	// private Integer widthPixels;
	/** Pixel height. */
	// private Integer heightPixels;
	/** Frame rate in frames-per-second (approximate). */
	private Double frameRateFps;

	/** Aspect ratio indicator (Blu-ray value: 0x02=4:3, 0x03=16:9). */
	private Integer aspectRatio;

	private Integer videoFormat;// video format code

	private Integer frameRate;// frame rate code

	// ---- audio-specific ----

	/** Number of audio channels. */
	private Integer channels;

	/** Sample rate in Hz. */
	private Integer sampleRateHz;

	/**
	 * Returns a human-readable stream category label.
	 */
	public String getCategory() {
		if (codingType == null)
			return "UNKNOWN";
		if (codingType.isVideo())
			return "VIDEO";
		if (codingType.isAudio())
			return "AUDIO";
		if (codingType.isSubtitle())
			return "SUBTITLE";
		if (codingType.isMenu())
			return "MENU";
		return "OTHER";
	}

}
