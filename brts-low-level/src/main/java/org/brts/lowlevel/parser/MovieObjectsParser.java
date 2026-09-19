package org.brts.lowlevel.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/parser/MovieObjectsParser.java' is part of BRTS.
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

import org.brts.common.exception.ParseException;
import org.brts.common.io.BinaryReader;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.model.bdmv.MovieObjects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@code BDMV/MovieObject.bdmv}.
 * <p>
 * Reads the binary format produced by {@link org.brts.lowlevel.bdmv.MovieObjectsWriter MovieObjectsWriter} and returns
 * a populated {@link MovieObjects} model.
 * <p>
 * Binary layout (all integers big-endian):
 *
 * <pre>
 * File header (40 bytes):
 *   magic                       : 4 bytes  ("MOBJ")
 *   version                     : 4 bytes  ("0200" or "0300")
 *   ExtensionDataStartAddress   : 4 bytes  (0 when no extension data)
 *   reserved                    : 28 bytes
 *
 * MovieObjects section (starts at 40):
 *   section_length              : 4 bytes
 *   reserved                    : 4 bytes
 *   number_of_movie_objects     : 2 bytes
 *   movie_object[N]:
 *     flags                     : 1 byte  (resume_intention | menu_call_mask | title_search_mask | ...)
 *     reserved                  : 1 byte
 *     number_of_navigation_cmds : 2 bytes
 *     navigation_command[M]:
 *       opcode                  : 4 bytes
 *       operand1 (dst)          : 4 bytes
 *       operand2 (src)          : 4 bytes
 * </pre>
 */
public class MovieObjectsParser implements BinaryParser<MovieObjects> {

	private static final String MAGIC = "MOBJ";

	private static final String VERSION_200 = "0200";

	private static final String VERSION_300 = "0300";

	@Override
	public MovieObjects parse(InputStream input) throws IOException {
		try (BinaryReader r = new BinaryReader(input)) {
			return readMovieObjects(r);
		}
	}

	private MovieObjects readMovieObjects(BinaryReader r) throws IOException {
		// ---- File header (40 bytes) ----
		String magic = r.readAscii(4);
		if (!MAGIC.equals(magic)) {
			throw new ParseException(
					"Not a MovieObject.bdmv file: expected magic '" + MAGIC + "', got '" + magic + "'");
		}
		String version = r.readAscii(4);
		if (!VERSION_200.equals(version) && !VERSION_300.equals(version)) {
			throw new ParseException("Unsupported MovieObject.bdmv version: '" + version + "'");
		}

		long extensionStartAddress = r.readUnsignedInt(); // may be 0
		r.skip(28); // reserved — total header is 40 bytes

		// ---- MovieObjects section ----
		long sectionLength = r.readUnsignedInt(); // length of the body that follows
		r.skip(4); // reserved
		int numObjects = r.readUnsignedShort();

		List<MovieObjects.MovieObject> objs = new ArrayList<>(numObjects);
		for (int i = 0; i < numObjects; i++) {
			objs.add(readObject(r));
		}

		MovieObjects mo = new MovieObjects();
		mo.setMovieObjects(objs);
		return mo;
	}

	/**
	 * Reads a single movie object: 1-byte flags, 1 byte reserved, 2-byte command count, then 12 bytes per command.
	 */
	private MovieObjects.MovieObject readObject(BinaryReader r) throws IOException {
		int flagsByte = r.readUnsignedByte();
		r.skip(1); // reserved

		int numCommands = r.readUnsignedShort();

		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setResumeIntentionFlag((flagsByte & 0x80) != 0);
		obj.setMenuCallMask((flagsByte & 0x40) != 0);
		obj.setTitleSearchMask((flagsByte & 0x20) != 0);

		List<MovieObjects.NavigationCommand> cmds = new ArrayList<>(numCommands);
		for (int c = 0; c < numCommands; c++) {
			cmds.add(readCommand(r));
		}
		obj.setNavigationCommands(cmds);
		return obj;
	}

	/**
	 * Reads a single 12-byte navigation command (opcode + two operands).
	 */
	private MovieObjects.NavigationCommand readCommand(BinaryReader r) throws IOException {
		long opcode = r.readUnsignedInt();
		long op1 = r.readUnsignedInt();
		long op2 = r.readUnsignedInt();

		ParsedNavigationCommand parsed = ParsedNavigationCommand.of(opcode, op1, op2);
		return MovieObjects.NavigationCommand.fromParsed(parsed);
	}

}
