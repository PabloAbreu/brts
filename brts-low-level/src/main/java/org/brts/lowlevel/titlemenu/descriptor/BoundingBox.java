package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/BoundingBox.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * An optional absolute bounding box (in pixels) that constrains button placement. When set on a {@link LayoutConfig},
 * it replaces the margin-based usable area entirely: the layout engine positions buttons within ({@code x}, {@code y},
 * {@code width}, {@code height}) instead of deriving the area from screen dimensions minus margins.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoundingBox {

	/** X origin of the bounding box in pixels. */
	private Integer x;

	/** Y origin of the bounding box in pixels. */
	private Integer y;

	/** Width of the bounding box in pixels. */
	private Integer width;

	/** Height of the bounding box in pixels. */
	private Integer height;

	/**
	 * Returns {@code true} when all four fields are set and dimensions are positive.
	 */
	public boolean isValid() {
		return x != null && y != null && width != null && height != null && width > 0 && height > 0;
	}
}
