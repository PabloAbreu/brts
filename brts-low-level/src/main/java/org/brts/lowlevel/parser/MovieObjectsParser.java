package org.brts.lowlevel.parser;

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
 *   MovieObjectsStartAddress    : 4 bytes
 *   ExtensionDataStartAddress   : 4 bytes  (0 when no extension data)
 *   reserved                    : 24 bytes
 *
 * MovieObjects section (starts at MovieObjectsStartAddress):
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

		long objectsStartAddress = r.readUnsignedInt();
		long extensionStartAddress = r.readUnsignedInt(); // may be 0
		r.skip(24); // reserved — total header is 40 bytes

		// ---- Seek to MovieObjects section ----
		long pos = r.getPosition();
		if (objectsStartAddress > pos) {
			r.skip(objectsStartAddress - pos);
		}

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
