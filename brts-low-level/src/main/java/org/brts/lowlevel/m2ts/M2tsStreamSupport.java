/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsStreamSupport.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import java.util.Set;

import org.brts.common.model.StreamCodingType;

/** Shared codec support policy for M2TS shrink and preservation workflows. */
public final class M2tsStreamSupport {
	public static final Set<StreamCodingType> SUPPORTED_SHRINK_TYPES = Set.of(StreamCodingType.H264_AVC,
			StreamCodingType.H265_HEVC, StreamCodingType.DOLBY_AC3, StreamCodingType.DOLBY_AC3_PLUS,
			StreamCodingType.DOLBY_TRUEHD, StreamCodingType.DTS, StreamCodingType.DTS_HD,
			StreamCodingType.DTS_HD_MASTER_AUDIO, StreamCodingType.DTS_EXPRESS, StreamCodingType.PRESENTATION_GRAPHICS);

	private M2tsStreamSupport() {
	}
}
