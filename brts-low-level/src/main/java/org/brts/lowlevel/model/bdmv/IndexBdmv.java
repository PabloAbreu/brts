package org.brts.lowlevel.model.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/bdmv/IndexBdmv.java' is part of BRTS.
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

import java.util.List;

/**
 * Model for {@code BDMV/index.bdmv} — the disc top-level index.
 * <p>
 * Specifies:
 * <ul>
 * <li>The title to play at first-play (usually a menu or first movie)</li>
 * <li>The top-menu title reference</li>
 * <li>A table of all titles (each mapped to a MovieObject or BD-J object)</li>
 * </ul>
 * <p>
 * Supports both HDMV (MovieObject) and BD-J title entry types.
 */
@Getter
@Setter
public class IndexBdmv {

	/** Disc version string (e.g. "0200", "0300"). */
	private String version;

	/** Disc application type (lower nibble of flags byte; 1 = Blu-ray disc). */
	private int discApplicationType = 1;

	/**
	 * Content provider name (32-byte null-padded string from AppInfoBDMV). May be blank.
	 */
	private String contentProviderName;

	/** First-play title. */
	private TitleEntry firstPlayTitle;

	/** Top-menu title. Null if no top menu is defined. */
	private TitleEntry topMenuTitle;

	/** All user-accessible titles (index 0 = title 1, etc.). */
	private List<TitleEntry> titles;

	/**
	 * Reference to a MovieObject (HDMV) or BD-J object.
	 * <p>
	 * In the binary format, the object type and access type are packed into the first byte: bits 7-6 = object_type
	 * (1=HDMV, 2=BD-J), bits 5-4 = access_type. HDMV entries carry a 16-bit {@link #hdmvObjectId}; BD-J entries carry a
	 * 5-character {@link #bdjObjectName} referencing a {@code .bdjo} file.
	 */
	@Getter
	@Setter
	public static class TitleEntry {

		/** Object type in the binary format: 1 = HDMV (MovieObject), 2 = BD-J. */
		private int objectType = 1;

		/**
		 * Access type: 0 = prohibited, 2 = permitted (title button accessible). Packed into bits 5-4 of the first entry
		 * byte.
		 */
		private int accessType = 0;

		/**
		 * Index into {@code MovieObject.bdmv} object table (zero-based). Only used when objectType == 1 (HDMV).
		 */
		private int hdmvObjectId;

		/**
		 * BD-J object name (5-character ASCII, references a {@code .bdjo} file). Only used when objectType == 2 (BD-J).
		 */
		private String bdjObjectName;

		/**
		 * Raw value of the secondary flags byte (byte 4 of the 12-byte entry). 0: HDMV movie, 1: HDMV interactive, 2:
		 * BD-J movie, 3: BD-J interactive.
		 *
		 */
		private int playbackType;

		/** Returns {@code true} if this entry refers to a BD-J application. */
		public boolean isBdj() {
			return objectType == 2;
		}

		/** Returns {@code true} if this entry refers to an HDMV MovieObject. */
		public boolean isHdmv() {
			return objectType == 1;
		}

	}

}
