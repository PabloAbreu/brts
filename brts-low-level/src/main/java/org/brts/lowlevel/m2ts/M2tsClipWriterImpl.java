package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsClipWriterImpl.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Path;

import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class M2tsClipWriterImpl implements M2tsClipWriter {

	@Override
	public void write(M2tsDescriptor descriptor, Path m2tsPath, Path clpiPath) throws IOException {
		// TODO Auto-generated method stub

		// --- Write M2TS ---
		M2tsWriter writer = new M2tsWriter();
		writer.write(descriptor, m2tsPath);
		log.info("M2TS written → " + m2tsPath);

		// --- Build and write matching CLPI ---
		int applicationType = ClipInfo.APPLICATION_TYPE_MOVIE;
		// if only one stream and it's a IGS, set application type to interactive graphics
		if (descriptor.getStreams().size() == 1 && descriptor.getStreams().get(0)
				.getStreamTypeByte() == StreamCodingType.INTERACTIVE_GRAPHICS.getCodingTypeByte()) {
			applicationType = ClipInfo.APPLICATION_TYPE_INTERACTIVE_GRAPHICS;
		}
		ClipInfo clipInfo = writer.buildClipInfo(descriptor, clpiPath.getFileName().toString().split("\\.")[0],
				applicationType);
		new ClipInfoWriter().write(clipInfo, clpiPath);
		log.info("CLPI written → " + clpiPath);
	}

}
