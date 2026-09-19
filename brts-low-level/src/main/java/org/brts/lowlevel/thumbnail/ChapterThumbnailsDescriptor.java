package org.brts.lowlevel.thumbnail;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/thumbnail/ChapterThumbnailsDescriptor.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON descriptor listing the thumbnails extracted from the chapters of a source media file.
 * <p>
 * Image paths are relative to the directory holding the descriptor.
 */
@Getter
@Setter
public class ChapterThumbnailsDescriptor {

	/** Source media file the thumbnails were extracted from. */
	private String sourceMedia;

	/** Width in pixels of every generated thumbnail. */
	private int thumbnailWidth;

	/** Height in pixels of every generated thumbnail. */
	private int thumbnailHeight;

	private List<ChapterThumbnail> chapters = new ArrayList<>();

	@Getter
	@Setter
	public static class ChapterThumbnail {

		/** 1-based chapter index. */
		private int index;

		/** Chapter display name, or a generated {@code Chapter NN} label when the source has no title. */
		private String title;

		/** Chapter start time in seconds. */
		private double startTimeSeconds;

		/** Chapter start time in 90 kHz ticks. */
		private long startTimeTicks;

		/** Descriptor-relative path of the PNG thumbnail. */
		private String imageFile;

	}

}
