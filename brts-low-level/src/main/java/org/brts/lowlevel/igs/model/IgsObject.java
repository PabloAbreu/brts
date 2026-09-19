package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsObject.java' is part of BRTS.
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

/**
 * Object Definition Segment (ODS) fragment, or a logical RLE-compressed bitmap reconstructed from its fragments, used
 * for button graphics or subtitle imagery.
 * <p>
 * When the object spans multiple segments, each fragment carries the same {@link #id} and the
 * {@link #sequenceDescriptor} indicates first/last. Only the first fragment carries {@link #dataLength}, {@link #width}
 * and {@link #height}. The complete RLE data is the concatenation of all fragments' {@link #rleData}.
 */
@Getter
@Setter
@ToString(exclude = "rleData")
public class IgsObject {

	/** PTS from the PES header (90 kHz ticks). */
	private long pts;

	/** Object id (16 bits). */
	private int id;

	/** Object version number (8 bits). */
	private int version;

	/** Sequence descriptor (first/last fragment flags). */
	private SequenceDescriptor sequenceDescriptor;

	/** Total logical data length (24 bits), present only on the first fragment. */
	private int dataLength;

	/** Width in pixels, present only on the first fragment. */
	private int width;

	/** Height in pixels, present only on the first fragment. */
	private int height;

	/**
	 * Raw RLE-encoded bitmap bytes. This contains one physical fragment in parser or producer display sets and the
	 * complete bitmap in objects returned by the IGS demuxer.
	 */
	private byte[] rleData;

}
