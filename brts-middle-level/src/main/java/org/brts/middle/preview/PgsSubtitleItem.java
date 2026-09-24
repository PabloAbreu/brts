package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/PgsSubtitleItem.java' is part of BRTS.
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
import org.brts.lowlevel.igs.model.CompositionObject;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A single navigable subtitle "show" event — a PGS display set whose composition carries at least one object.
 */
@Getter
@Setter
public class PgsSubtitleItem {

	/** Zero-based index among navigable items. */
	private int index;

	/** PTS (90 kHz ticks) at which this item starts being displayed. */
	private long showPtsTicks;

	/** PTS (90 kHz ticks) at which this item stops being displayed, or -1 if unknown (e.g. last item). */
	private long hidePtsTicks = -1;

	/** Decoded bitmap plus its screen position, one per composition object. */
	private List<RenderedObject> renderedObjects = new ArrayList<>();

	/** A decoded composition object bitmap together with its on-screen placement. */
	@Getter
	@Setter
	public static class RenderedObject {

		private CompositionObject compositionObject;

		private BufferedImage image;

		public RenderedObject(CompositionObject compositionObject, BufferedImage image) {
			this.compositionObject = compositionObject;
			this.image = image;
		}

	}

}
