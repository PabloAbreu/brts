package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsClipWriterFactory.java' is part of BRTS.
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

import org.brts.common.utils.TsMuxerUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Chooses the appropriate {@link M2tsClipWriter} implementation based on the runtime environment.
 */
@Slf4j
public class M2tsClipWriterFactory {

	public static M2tsClipWriter createWriter() {
		if (TsMuxerUtils.isTsMuxeRAvailable())
			return new TsMuxerM2tsClipWriter();
		log.warn("tsMuxeR not found in PATH or configured via properties; falling back to basic M2TS writer (buggy).");
		return new M2tsClipWriterImpl();
	}

}
