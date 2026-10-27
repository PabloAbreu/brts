/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/BitrateCode.java' is part of BRTS.
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

package org.brts.common.m2ts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BitrateCode {

	// ATSC A/52 AC-3 bit_rate_code values 0 through 18, in kbps.
	AC3_32kbps(0, 32),
	AC3_40kbps(1, 40),
	AC3_48kbps(2, 48),
	AC3_56kbps(3, 56),
	AC3_64kbps(4, 64),
	AC3_80kbps(5, 80),
	AC3_96kbps(6, 96),
	AC3_112kbps(7, 112),
	AC3_128kbps(8, 128),
	AC3_160kbps(9, 160),
	AC3_192kbps(10, 192),
	AC3_224kbps(11, 224),
	AC3_256kbps(12, 256),
	AC3_320kbps(13, 320),
	AC3_384kbps(14, 384),
	AC3_448kbps(15, 448),
	AC3_512kbps(16, 512),
	AC3_576kbps(17, 576),
	AC3_640kbps(18, 640);

	private @Getter final int code;

	private @Getter final int bitrateKbps;

	public static BitrateCode fromCode(int code) {
		for (BitrateCode bc : values()) {
			if (bc.code == code) {
				return bc;
			}
		}
		return null; // unknown bitrate code
	}

	public static BitrateCode fromBitrate(int bitrateKbps) {
		for (BitrateCode bc : values()) {
			if (bc.bitrateKbps == bitrateKbps) {
				return bc;
			}
		}
		return null; // unknown bitrate
	}

}
