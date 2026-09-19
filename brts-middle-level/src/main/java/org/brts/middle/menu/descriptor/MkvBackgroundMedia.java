package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/MkvBackgroundMedia.java' is part of BRTS.
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
 * Background media descriptor for MKV files.
 * <p>
 * The MKV is used to extract the video and audio elementary streams that form the background of the setup menu.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkvBackgroundMedia extends BackgroundMediaDescriptor {

	/** Path to the MKV file on disk. */
	private String file;

	/**
	 * Optional: MKV track number for the video stream to extract. If {@code null}, the first video track is used.
	 */
	private Integer videoTrackNumber;

	/**
	 * Optional: MKV track number for the audio stream to extract. If {@code null}, the first audio track is used.
	 */
	private Integer audioTrackNumber;

}
