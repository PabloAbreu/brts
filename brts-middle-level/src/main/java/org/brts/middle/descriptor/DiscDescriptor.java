package org.brts.middle.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/descriptor/DiscDescriptor.java' is part of BRTS.
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

import org.brts.common.menu.TextStyle;
import jakarta.validation.constraints.Pattern;
import org.brts.common.validation.WritableDirectory;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Middle-level descriptor for a complete Blu-ray disc.
 * <p>
 * This is the main input for the middle-level authoring pipeline. Example {@code disc.json}:
 *
 * <pre>{@code
 * {
 *   "discName": "My Movie",
 *   "hasTopMenu": false,
 *   "titles": [
 *     {
 *       "titleId": 1,
 *       "sourceMkv": "/videos/movie.mkv",
 *       "audioLanguages": ["eng"],
 *       "chapters": [{ "timeSeconds": 0 }, { "timeSeconds": 3600 }]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class DiscDescriptor {

	/** Name of the disc. */
	@NotBlank
	@Pattern(regexp = "[A-Za-z0-9 _]+", message = "discName must not contain special characters")
	private String discName;

	/**
	 * Output folder for the generated Blu-ray disc.
	 *
	 * Final paths will look like outputFolder/discName/BDMV/ ...
	 */
	@NotBlank
	@WritableDirectory(createIfMissing = true)
	private String outputFolder;// for the BD

	/** If true, a top menu object will be generated (requires menu title config). */
	private boolean hasTopMenu = false;

	/**
	 * Optional title menu configuration. When non-null and the disc has more than one title, a selectable title menu is
	 * generated and wired as First Play and Top Menu.
	 */
	@Valid
	private TitleMenuConfig titleMenuConfig;

	/**
	 * Global text style for all disc menus (title menu and popup menus). Serves as the base style that more specific
	 * overrides (title menu style, popup style) are merged over. When {@code null}, defaults are resolved from
	 * {@code textStyle.*} properties in brts.conf at the lowest layer.
	 */
	@Valid
	private TextStyle style;

	/**
	 * Disc-wide popup menu style override. When non-null, merged over {@link #style} to produce the effective style for
	 * all popup menus on this disc. When {@code null}, the global {@link #style} (or brts.conf defaults) is used.
	 */
	@Valid
	private TextStyle popupStyle;

	/**
	 * Disc-wide popup menu configuration. When non-null, its presentation settings (layout, dimensions, style and
	 * backgrounds) are applied to all auto-generated popup menus. Track entries and output clip names are per-title.
	 */
	@Valid
	private PopupMenuConfig popupMenu;

	/**
	 * Disc-wide PGS subtitle rendering configuration applied to all titles during conversion. When null, defaults from
	 * brts.conf / low-level configuration are used.
	 */
	@Valid
	private PgsRenderConfig pgsConfig;

	/** List of titles on the disc. */
	@NotEmpty
	private List<@Valid TitleDescriptor> titles;

	@JsonIgnore
	@AssertTrue(message = "titleMenuConfig is required when hasTopMenu is true")
	public boolean isTopMenuConfigConsistent() {
		return !hasTopMenu || titleMenuConfig != null;
	}

}
