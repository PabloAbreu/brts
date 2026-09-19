package org.brts.lowlevel.model.disc;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/disc/LowLevelDiscDescriptor.java' is part of BRTS.
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

import java.util.List;

import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.mpls.MoviePlaylist;

import lombok.Getter;
import lombok.Setter;

/**
 * Descriptor for full disc creation.
 *
 * All given information is given explicitly.
 */
@Getter
@Setter
public class LowLevelDiscDescriptor {

	/**
	 * Disc name. Should follow ISO/IEC 13346, UDF 2.50 or whatever
	 */
	private String discName;

	/**
	 * Parent folder.
	 *
	 * Passing parentFolder=/some/path and discName=MY_MOVIE will lead to creation of /some/path/MY_MOVIE/BDMV etc...
	 */
	private String parentFolder;

	/**
	 * index.bdmv model
	 */
	private IndexBdmv index;

	/**
	 * MovieObject.bdmv model
	 */
	private MovieObjects movieObjects;

	/**
	 * contents of BDMV/PLAYLIST/
	 */
	private List<MoviePlaylist> playlists;

}
