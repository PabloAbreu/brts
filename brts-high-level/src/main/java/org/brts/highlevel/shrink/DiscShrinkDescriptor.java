/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/main/java/org/brts/highlevel/shrink/DiscShrinkDescriptor.java' is part of BRTS.
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

package org.brts.highlevel.shrink;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Optional global and per-playlist language preferences for disc shrinking. */
@Getter
@Setter
@NoArgsConstructor
public class DiscShrinkDescriptor {

	/** Null retains all audio tracks; otherwise contains ISO 639-2 language codes. */
	private List<String> audioLanguages;

	/** Null retains all subtitle tracks; otherwise contains ISO 639-2 language codes. */
	private List<String> subtitleLanguages;

	/** Optional preferences keyed by five-digit playlist ID; null fields inherit the global preference. */
	private Map<String, PlaylistPreferences> playlists;

	@Getter
	@Setter
	@NoArgsConstructor
	public static class PlaylistPreferences {
		private List<String> audioLanguages;
		private List<String> subtitleLanguages;
	}
}
