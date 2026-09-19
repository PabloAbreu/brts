package org.brts.middle.menu.media;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/media/MediaSourceFactory.java' is part of BRTS.
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

import org.brts.middle.menu.descriptor.BackgroundMediaDescriptor;
import org.brts.middle.menu.descriptor.MkvBackgroundMedia;

import java.nio.file.Path;

/**
 * Factory that creates the appropriate {@link MediaSource} implementation from a {@link BackgroundMediaDescriptor}.
 */
public final class MediaSourceFactory {

	private MediaSourceFactory() {
	}

	/**
	 * Creates a {@link MediaSource} from the given descriptor.
	 *
	 * @param descriptor the background media descriptor
	 * @return the appropriate media source implementation
	 * @throws IllegalArgumentException if the descriptor type is unsupported
	 */
	public static MediaSource create(BackgroundMediaDescriptor descriptor) {
		if (descriptor instanceof MkvBackgroundMedia mkv) {
			return new MkvMediaSource(Path.of(mkv.getFile()), mkv.getVideoTrackNumber(), mkv.getAudioTrackNumber());
		}
		throw new IllegalArgumentException(
				"Unsupported background media type: " + descriptor.getClass().getSimpleName());
	}

}
