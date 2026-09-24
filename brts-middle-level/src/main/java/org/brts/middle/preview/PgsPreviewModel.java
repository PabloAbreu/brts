package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/PgsPreviewModel.java' is part of BRTS.
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
import org.brts.lowlevel.igs.model.VideoDescriptor;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the runtime state for a PGS preview session: the parsed, navigable subtitle items and the background rendering
 * mode.
 */
@Getter
@Setter
public class PgsPreviewModel {

	public enum BackgroundMode {
		CHECKERBOARD, VIDEO_SNAPSHOT
	}

	/** Screen dimensions shared by all display sets in this stream. */
	private VideoDescriptor videoDescriptor;

	/** Navigable subtitle items, ordered by PTS. Item PTS fields remain in the stream's raw, absolute clock. */
	private List<PgsSubtitleItem> items = new ArrayList<>();

	/**
	 * Baseline PTS (90 kHz ticks) that this clip's timeline starts at, e.g. the lowest PCS PTS observed. BD-authored
	 * M2TS clips do not necessarily start their PTS numbering at zero, so displayed timestamps must be shown relative
	 * to this baseline (video snapshot seeking, however, must keep using the raw absolute PTS, since it shares the same
	 * clock domain as the video stream).
	 */
	private long basePtsTicks;

	/** Index of the currently displayed item within {@link #items}. */
	private int currentIndex;

	private BackgroundMode backgroundMode = BackgroundMode.CHECKERBOARD;

	/** Source m2ts file, only set when {@link #backgroundMode} is {@code VIDEO_SNAPSHOT}. */
	private Path videoSource;

	/** Lazily-populated video snapshot cache, keyed by subtitle item index. */
	private Map<Integer, BufferedImage> snapshotCache = new HashMap<>();

	public int getScreenWidth() {
		return videoDescriptor != null ? videoDescriptor.getWidth() : 1920;
	}

	public int getScreenHeight() {
		return videoDescriptor != null ? videoDescriptor.getHeight() : 1080;
	}

	public PgsSubtitleItem getCurrentItem() {
		if (currentIndex >= 0 && currentIndex < items.size()) {
			return items.get(currentIndex);
		}
		return null;
	}

	public void goToItem(int index) {
		if (index < 0 || index >= items.size()) {
			return;
		}
		currentIndex = index;
	}

	public void goToNext() {
		goToItem(currentIndex + 1);
	}

	public void goToPrevious() {
		goToItem(currentIndex - 1);
	}

}
