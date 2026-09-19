package org.brts.common.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/BitrateCode.java' is part of BRTS.
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BitrateCode {

	// all these values are partly given by IA, partly by reverse engineering real
	// Blu-ray PMT data.
	// They seem to be consistent across multiple discs
	AC3_128kbps(0x20, 128, 2), //
	AC3_192kbps(0x29, 192, 2), //
	AC3_320kbps(0x34, 320, 2), //
	AC3_320kbps_alias(0x35, 320, 2), //
	// not sure about this one
	AC3_448kbps(0x3C, 448, 6), //
	AC3_384kbps(0x18, 384, 2), //
	EAC3_896kbps(0x40, 896, 8), //
	AC3_640kbps(0x48, 640, 6), //
	AC3_640kbps_alias(0x46, 640, 6), // not sure about this one
	EAC3_1024kbps(0x50, 1024, 8), //
	TRUE_HD(0x48, -1, 8);// sometimes it's just 6 channels. bitrate is not constant

	private @Getter final int code;

	private @Getter final int bitrateKbps;

	private @Getter final int channels;

	public static BitrateCode fromCode(int code) {
		for (BitrateCode bc : values()) {
			if (bc.code == code) {
				return bc;
			}
		}
		return null; // unknown bitrate code
	}

	// when i find several codes for a given bitrate, i'll update this heuristic
	public static BitrateCode fromBitrate(int bitrateKbps) {
		for (BitrateCode bc : values()) {
			if (bc.bitrateKbps == bitrateKbps) {
				return bc;
			}
		}
		return null; // unknown bitrate
	}

}
