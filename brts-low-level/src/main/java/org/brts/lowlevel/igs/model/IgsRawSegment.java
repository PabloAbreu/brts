package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsRawSegment.java' is part of BRTS.
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
import lombok.ToString;
import org.brts.lowlevel.igs.IgsSegmentType;

/**
 * A raw PES-encapsulated segment as found in an IGS elementary stream.
 * <p>
 * The IGS stream is made up of PES packets. Each PES packet carries a PTS/DTS header followed by one segment. A segment
 * has a 3-byte header (type + length) plus the segment data bytes.
 * <p>
 * This class preserves the exact binary representation to enable faithful round-trip (demux → mux) without loss.
 */
@Getter
@Setter
@ToString(exclude = "segmentData")
public class IgsRawSegment {

	/** PTS from the enclosing PES header (90 kHz ticks). */
	private long pts;

	/** DTS from the enclosing PES header (90 kHz ticks, -1 if absent). */
	private long dts = -1;

	/** Segment type byte. */
	private IgsSegmentType type;

	/** Raw segment data (excluding the 3-byte type+length header). */
	private byte[] segmentData;

}
