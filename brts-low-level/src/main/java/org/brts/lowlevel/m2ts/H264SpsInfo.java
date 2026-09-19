package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/H264SpsInfo.java' is part of BRTS.
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
 * Minimal subset of SPS fields required to reconstruct per-frame POC values and to locate {@code pic_order_cnt_lsb}
 * inside each slice header.
 */
public final class H264SpsInfo {

	int picOrderCntType;

	int log2MaxPicOrderCntLsbMinus4; // valid when picOrderCntType == 0

	int log2MaxFrameNumMinus4;

	boolean frameMbsOnly;

	boolean separateColourPlane;

	/** {@code num_reorder_frames} from VUI {@code bitstream_restriction}; 0 if absent. */
	int numReorderFrames;

}
