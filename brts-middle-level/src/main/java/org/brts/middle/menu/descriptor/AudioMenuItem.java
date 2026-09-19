package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/AudioMenuItem.java' is part of BRTS.
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

/**
 * An audio track selection menu item.
 * <p>
 * Each audio item corresponds to one audio stream the user can select. {@link #getStreamNumber()} maps to PSR1 (primary
 * audio stream number) in Blu-ray navigation.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * {
 *   "description": "English DTS-HD",
 *   "streamNumber": 1,
 *   "style": { "normalColor": "#FFFFFF00" }
 * }
 * }</pre>
 */
@Getter
@Setter
public class AudioMenuItem extends StreamMenuItem {

}
