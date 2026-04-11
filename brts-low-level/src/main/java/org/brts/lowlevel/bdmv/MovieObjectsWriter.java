package org.brts.lowlevel.bdmv;

import org.brts.common.io.BinaryWriter;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.writer.BlurayFileWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writer for {@code BDMV/MovieObject.bdmv}.
 */
public class MovieObjectsWriter implements BlurayFileWriter<MovieObjects> {

	private static final String MAGIC = "MOBJ";

	private static final String VERSION = "0300";

	@Override
	public void write(MovieObjects model, OutputStream output) throws IOException {
		byte[] objSection = buildObjectsSection(model);

		// Header: 4 + 4 + 2×4 + 24 reserved = 40 bytes
		int headerSize = 40;
		int objectsOffset = headerSize;
		int extensionOffset = 0;

		BinaryWriter w = new BinaryWriter(output);
		w.writeAscii(MAGIC);
		w.writeAscii(VERSION);
		w.writeInt(objectsOffset);
		w.writeInt(extensionOffset);
		w.writePadding(24);

		w.writeBytes(objSection);
	}

	private byte[] buildObjectsSection(MovieObjects model) throws IOException {
		List<MovieObjects.MovieObject> objects = model.getMovieObjects() != null ? model.getMovieObjects() : List.of();

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		org.brts.common.io.BinaryWriter wi = new org.brts.common.io.BinaryWriter(inner);

		wi.writePadding(4); // reserved
		wi.writeShort(objects.size());

		for (MovieObjects.MovieObject obj : objects) {
			List<MovieObjects.NavigationCommand> cmds = obj.getNavigationCommands() != null
					? obj.getNavigationCommands() : List.of();

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
				}
				else {
					throw new IllegalStateException("rawOpcode must be set on NavigationCommand before writing. "
							+ "Use NavigationCommandCompiler.compile() with explicit immediate flags.");
				}
				wi.writeInt(cmd.getOperand1());
				wi.writeInt(cmd.getOperand2());
			}
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

}
