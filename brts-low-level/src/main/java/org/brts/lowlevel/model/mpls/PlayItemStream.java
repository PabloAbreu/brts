package org.brts.lowlevel.model.mpls;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/mpls/PlayItemStream.java' is part of BRTS.
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

import lombok.Getter;
import lombok.Setter;

import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.model.StreamCodingType;

/**
 * Stream entry in a PlayItem STN (Stream Number Table). Specifies which PID to present and carries the same stream
 * attributes as the corresponding CLPI ClipStream entry.
 */
@Getter
@Setter
public class PlayItemStream implements IStreamInfo {
	public static final int STREAM_TYPE_IN_MUX = 0x01;
	public static final int STREAM_TYPE_OUT_OF_MUX = 0x02;
	// so says chatgpt
	public static final int STREAM_TYPE_REPEATABLE_OUT_OF_MUX = 0x03;
	/** PID of the stream within the transport stream. */
	private int pid;

	/** Stream type: 0x01=in-mux, 0x02=out-of-mux, 0x03=repeatable out-of-mux, etc. */
	private int streamType; // 0x01=in-mux, 0x02=out-of-mux, etc.

	/** Sub-path index (applicable for stream types 2, 3, 4). */
	private int subpathId;

	/** Sub-clip index within the sub-path (applicable for stream type 2 only). */
	private int subclipId;

	/** Coding type of the stream (video, audio, subtitle, etc.). */
	private StreamCodingType codingType;

	/** ISO 639-2 language code (applicable for audio / subtitle streams). */
	private String language;

	// Attributes mirror ClipStream — duplicated here as the MPLS carries its own
	// copy.

	/** Video format code (mirrors ClipStream attribute). */
	private Integer videoFormat;// video format code

	/** Frame rate code (mirrors ClipStream attribute). */
	private Integer frameRate;// frame rate code

	/** Audio channel layout (mirrors ClipStream attribute). */
	private Integer audioChannelLayout;

	/** Audio sample rate code (mirrors ClipStream attribute). */
	private Integer sampleRate;

	public Integer sampleRateKhz() {
		// TODO refactor this to have a single source of truth somewhere
		return sampleRate != null ? switch (sampleRate) {
		case 0x01 -> 48;// this one seems good
		default -> null;
		} : null;
	}

}
