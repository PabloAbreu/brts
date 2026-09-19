package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/MovieObjectsWriter.java' is part of BRTS.
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

import org.brts.common.io.ByteArrayBinaryWriter;
import org.brts.common.io.BinaryWriter;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.writer.BlurayFileWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writer for {@code BDMV/MovieObject.bdmv}.
 */
public class MovieObjectsWriter implements BlurayFileWriter<MovieObjects> {

	private static final String MAGIC = "MOBJ";

	private static final String VERSION = "0200";

	@Override
	public void write(MovieObjects model, OutputStream output) throws IOException {
		byte[] objSection = buildObjectsSection(model);

		// Header: 4 + 4 + 4 + 28 reserved = 40 bytes
		int extensionOffset = 0;

		@SuppressWarnings("resource")
		BinaryWriter w = new BinaryWriter(output);
		w.writeAscii(MAGIC);
		w.writeAscii(VERSION);
		w.writeInt(extensionOffset);
		w.writePadding(28); // reserved

		w.writeBytes(objSection);
	}

	private byte[] buildObjectsSection(MovieObjects model) throws IOException {
		List<MovieObjects.MovieObject> objects = model.getMovieObjects() != null ? model.getMovieObjects() : List.of();

		ByteArrayBinaryWriter wi = new ByteArrayBinaryWriter();

		wi.writePadding(4); // reserved
		wi.writeShort(objects.size());

		for (MovieObjects.MovieObject obj : objects) {
			List<MovieObjects.NavigationCommand> cmds = obj.getNavigationCommands() != null
					? obj.getNavigationCommands()
					: List.of();

			// flags byte
			int flags = 0;
			if (obj.isResumeIntentionFlag())
				flags |= 0x80;
			if (obj.isMenuCallMask())
				flags |= 0x40;
			if (obj.isTitleSearchMask())
				flags |= 0x20;
			wi.writeByte(flags);
			wi.writePadding(1); // reserved
			wi.writeShort(cmds.size()); // number_of_navigation_commands

			for (MovieObjects.NavigationCommand cmd : cmds) {
				// Each command is 12 bytes: 4 opcode + 4 dst operand + 4 src operand
				if (cmd.getRawOpcode() >= 0) {
					wi.writeInt(cmd.getRawOpcode());
				} else {
					throw new IllegalStateException("rawOpcode must be set on NavigationCommand before writing. "
							+ "Use NavigationCommandCompiler.compile() with explicit immediate flags.");
				}
				wi.writeInt(cmd.getOperand1());
				wi.writeInt(cmd.getOperand2());
			}
		}

		return wi.toSizePrefixedByteArray();
	}

}
