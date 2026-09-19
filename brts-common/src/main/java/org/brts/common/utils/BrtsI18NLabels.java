package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/BrtsI18NLabels.java' is part of BRTS.
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

public class BrtsI18NLabels {
	public static final String MENU_TO_AUDIO = "i18n.output.menu.to.audio";
	public static final String MENU_TO_SUBTITLES = "i18n.output.menu.to.subtitles";
	public static final String MENU_EXIT = "i18n.output.menu.exit";
	public static final String MENU_BACK = "i18n.output.menu.back";
	public static final String MENU_SETTINGS = "i18n.output.menu.settings";

	public static String getLabel(String language, String key) {
		return BrtsI18N.getInstance().getMessage(language, key);
	}

	public static String getLabel(String key) {
		return BrtsI18N.getInstance().getMessage(BrtsI18N.getInstance().getDefaultLanguage(), key);
	}
}
