package org.brts.lowlevel.parser;

import org.brts.common.exception.ParseException;
import org.brts.common.io.BinaryReader;
import org.brts.lowlevel.model.bdmv.IndexBdmv;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@code BDMV/index.bdmv}.
 * <p>
 * Supports all known versions of the INDX format:
 * <ul>
 * <li>{@code "0100"} — AVCHD (HD DVD / early camcorder); HDMV-only, no BD-J.</li>
 * <li>{@code "0200"} — BD-ROM v2; HDMV and BD-J entries.</li>
 * <li>{@code "0300"} — BD-ROM v3; same binary structure as v2.</li>
 * </ul>
 * The binary entry layout is identical across all versions (12 bytes per title entry);
 * the version is surfaced in {@link IndexBdmv#getVersion()} so callers can apply
 * version-specific rules (e.g. the writer rejects BD-J entries for {@code "0100"}).
 * <p>
 * Binary layout (all integers big-endian): <pre>
 * File header (40 bytes):
 *   magic                       : 4 bytes  ("INDX")
 *   version                     : 4 bytes  ("0200" or "0300")
 *   IndexTableStartAddress      : 4 bytes
 *   ExtensionDataStartAddress   : 4 bytes
 *   reserved                    : 24 bytes
 *
 * AppInfoBDMV section (starts at offset 40):
 *   section_length              : 4 bytes
 *   reserved                    : 1 byte
 *   flags                       : 1 byte  (initial_output_mode_preference | content_exist_flag | ...)
 *   content_provider_name       : 32 bytes (null-padded ASCII)
 *
 * IndexTable section (starts at IndexTableStartAddress):
 *   section_length              : 4 bytes
 *   first_play_title            : 12 bytes  (see TitleEntry layout below)
 *   top_menu_title              : 12 bytes
 *   number_of_titles            : 2 bytes
 *   title[number_of_titles]     : 12 bytes each
 *
 * TitleEntry layout (12 bytes):
 *   byte  0: object_type (bits 7-6) | access_type (bits 5-4) | reserved (bits 3-0)
 *             object_type: 1 = HDMV, 2 = BD-J
 *             access_type: 0 = prohibited, 2 = permitted
 *   bytes 1-3: reserved
 *   byte  4: playback_type flags (bits 7-6) | reserved
 *   byte  5: reserved
 *   bytes 6-10:
 *     if BD-J  → bdj_object_name (5 ASCII chars, no null terminator)
 *     if HDMV  → reserved (4 bytes) + hdmv_object_id (2 bytes at offset 6-7) + reserved
 *   byte  11: reserved / null terminator
 * </pre>
 */
public class IndexBdmvParser implements BinaryParser<IndexBdmv> {

	private static final String MAGIC = "INDX";

	private static final String VERSION_100 = "0100";

	private static final String VERSION_200 = "0200";

	private static final String VERSION_300 = "0300";

	/** Bytes consumed by a single title entry in the index table (1+3+1+1+5+1). */
	@SuppressWarnings("unused")
	private static final int ENTRY_SIZE = 12;

	/**
	 * Length of the BD-J object name field (5 ASCII characters), located at byte offset 6
	 * within each title entry.
	 */
	private static final int BDJ_NAME_LENGTH = 5;

	@Override
	public IndexBdmv parse(InputStream input) throws IOException {
		try (BinaryReader r = new BinaryReader(input)) {
			return readIndex(r);
		}
	}

	private IndexBdmv readIndex(BinaryReader r) throws IOException {
		// ---- File header (40 bytes) ----
		String magic = r.readAscii(4);
		if (!MAGIC.equals(magic)) {
			throw new ParseException("Not an index.bdmv file: expected magic '" + MAGIC + "', got '" + magic + "'");
		}
		String version = r.readAscii(4);
		if (!VERSION_100.equals(version) && !VERSION_200.equals(version) && !VERSION_300.equals(version)) {
			throw new ParseException("Unsupported index.bdmv version: '" + version + "'");
		}

		long indexTableAddress = r.readUnsignedInt();
		r.readUnsignedInt(); // ExtensionDataStartAddress — may be 0, not used during
								// parsing
		r.skip(24); // reserved — total header is 40 bytes

		// ---- AppInfoBDMV section (always present between header and index table) ----
		String contentProviderName = readAppInfoBdmv(r);

		// ---- Seek to IndexTable section ----
		long pos = r.getPosition();
		if (indexTableAddress > pos) {
			r.skip(indexTableAddress - pos);
		}

		// ---- Index table ----
		IndexBdmv index = new IndexBdmv();
		index.setVersion(version);
		index.setContentProviderName(contentProviderName);

		/*
		 * section_length (4 bytes) — the body that follows is section_length bytes long
		 */
		r.readUnsignedInt(); // consume but not needed for parsing

		index.setFirstPlayTitle(readTitleEntry(r));
		index.setTopMenuTitle(readTitleEntry(r));

		int numberOfTitles = r.readUnsignedShort();
		List<IndexBdmv.TitleEntry> titles = new ArrayList<>(numberOfTitles);
		for (int i = 0; i < numberOfTitles; i++) {
			titles.add(readTitleEntry(r));
		}
		index.setTitles(titles);

		return index;
	}

	/**
	 * Reads the AppInfoBDMV section and returns the content_provider_name (trimmed of
	 * nulls). The section structure is: section_length(4) + reserved(1) + flags(1) +
	 * name(32).
	 */
	private String readAppInfoBdmv(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		if (sectionLength < 2) {
			// Malformed or missing AppInfoBDMV — skip what's there and return empty name
			if (sectionLength > 0)
				r.skip(sectionLength);
			return "";
		}
		r.skip(1); // reserved
		r.skip(1); // flags byte (initial_output_mode_preference | content_exist_flag |
					// ...)
		// content_provider_name: 32 bytes, null-padded
		byte[] nameBytes = r.readBytes(32);
		// Skip any remaining bytes in the section beyond the 34 bytes we've consumed
		long consumed = 2 + 32; // reserved(1) + flags(1) + name(32)
		if (sectionLength > consumed) {
			r.skip(sectionLength - consumed);
		}
		// Trim trailing null bytes
		int len = 0;
		for (int i = 0; i < nameBytes.length; i++) {
			if (nameBytes[i] != 0)
				len = i + 1;
		}
		return new String(nameBytes, 0, len, java.nio.charset.StandardCharsets.US_ASCII);
	}

	/**
	 * Reads a single 12-byte title entry.
	 *
	 * <pre>
	 * byte  0: object_type(2) | access_type(2) | reserved(4)
	 * bytes 1-3: reserved
	 * byte  4: playback_type(2) | reserved(6)
	 * byte  5: reserved
	 * bytes 6-10: bdj_object_name (BD-J) or hdmv_object_id at [6-7] (HDMV)
	 * byte  11: reserved
	 * </pre>
	 */
	private IndexBdmv.TitleEntry readTitleEntry(BinaryReader r) throws IOException {
		int flagByte = r.readUnsignedByte(); // byte 0
		r.skip(3); // bytes 1-3 reserved
		int playbackByte = r.readUnsignedByte(); // byte 4
		r.skip(1); // byte 5 reserved
		byte[] payload = r.readBytes(BDJ_NAME_LENGTH); // bytes 6-10
		r.skip(1); // byte 11 reserved

		int objectType = (flagByte >> 6) & 0x03;
		int accessType = (flagByte >> 4) & 0x03;
		int playbackType = (playbackByte >> 6) & 0x03;

		IndexBdmv.TitleEntry entry = new IndexBdmv.TitleEntry();
		entry.setObjectType(objectType);
		entry.setAccessType(accessType);
		entry.setPlaybackType(playbackType);

		if (objectType == 2) {
			// BD-J entry: payload bytes 0-4 are the 5-char BDJO file name
			entry.setBdjObjectName(new String(payload, java.nio.charset.StandardCharsets.US_ASCII));
		}
		else if (objectType == 1) {
			// HDMV entry: hdmv_object_id is a 16-bit big-endian value at payload[0-1]
			int hdmvObjectId = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
			entry.setHdmvObjectId(hdmvObjectId);
		}
		// objectType == 0 → reserved / empty slot; leave all fields at defaults

		return entry;
	}

}
