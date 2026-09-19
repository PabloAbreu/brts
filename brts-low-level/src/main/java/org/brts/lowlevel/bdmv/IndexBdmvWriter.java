package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/IndexBdmvWriter.java' is part of BRTS.
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.brts.common.exception.WriteException;
import org.brts.common.io.BinaryWriter;
import org.brts.common.io.ByteArrayBinaryWriter;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.writer.BlurayFileWriter;

/**
 * Writer for {@code BDMV/index.bdmv}.
 * <p>
 * The version written is taken from {@link IndexBdmv#getVersion()}; if {@code null} or blank, {@code "0300"} is used as
 * a safe default (broadest BD-ROM compatibility).
 * <p>
 * Version-specific constraints enforced at write time:
 * <ul>
 * <li>{@code "0100"} (AVCHD) — BD-J title entries ({@code objectType == 2}) are not supported and will cause a
 * {@link WriteException}.</li>
 * <li>{@code "0200"} / {@code "0300"} — both HDMV and BD-J entries are accepted.</li>
 * </ul>
 * <p>
 * Binary layout written (big-endian throughout):
 *
 * <pre>
 * Header (40 bytes):
 *   magic                     : 4 bytes  ("INDX")
 *   version                   : 4 bytes  (from model, e.g. "0300")
 *   IndexTableStartAddress    : 4 bytes  (always 0x4e = 78)
 *   ExtensionDataStartAddress : 4 bytes  (0 — extension not written)
 *   reserved                  : 24 bytes
 *
 * AppInfoBDMV section (38 bytes at offset 40):
 *   section_length            : 4 bytes  (34)
 *   reserved                  : 1 byte
 *   flags                     : 1 byte   (initial_output_mode_preference | content_exist_flag | ...)
 *   content_provider_name     : 32 bytes (null-padded ASCII)
 *
 * IndexTable section (starts at 0x4e = 78):
 *   section_length            : 4 bytes
 *   first_play_title          : 12 bytes
 *   top_menu_title            : 12 bytes
 *   number_of_titles          : 2 bytes
 *   title[number_of_titles]   : 12 bytes each
 *
 * Each 12-byte title entry:
 *   byte  0: object_type(2 bits:7-6) | access_type(2 bits:5-4) | reserved(4 bits)
 *   bytes 1-3: reserved
 *   byte  4: playback_type(2 bits:7-6) | reserved
 *   byte  5: reserved
 *   bytes 6-10:
 *     HDMV → hdmv_object_id (2 bytes big-endian) + 3 reserved bytes
 *     BD-J → bdj_object_name (5 ASCII chars, space-padded if shorter)
 *   byte  11: reserved
 * </pre>
 */
public class IndexBdmvWriter implements BlurayFileWriter<IndexBdmv> {

	private static final String MAGIC = "INDX";
	// AVCHD not supported anywhere in BRTS for now
	// private static final String VERSION_0100 = "0100";
	private static final String VERSION_0200 = "0200"; // BLU RAY
	private static final String VERSION_0300 = "0300"; // 4k ?
	private static final String DEFAULT_VERSION = VERSION_0200;

	/** Versions that support BD-J title entries. */
	private static final Set<String> BDJ_CAPABLE_VERSIONS = Set.of(VERSION_0200, VERSION_0300);

	/**
	 * Fixed offset of the IndexTable section (header + AppInfoBDMV = 40 + 38 = 78).
	 */
	private static final int INDEX_TABLE_ADDR = 0x4e;

	/** Fixed body length of the AppInfoBDMV section. */
	private static final int APP_INFO_BODY_LEN = 34;

	/** Fixed length of a content_provider_name field. */
	private static final int PROVIDER_NAME_LEN = 32;

	/** Number of bytes in each title entry. */
	private static final int ENTRY_SIZE = 12;

	@Override
	public void write(IndexBdmv model, OutputStream output) throws IOException {
		String version = resolveVersion(model);
		validate(model, version);

		byte[] appInfo = buildAppInfoSection(model);
		byte[] indexTable = buildIndexTableSection(model);

		// BinaryWriter wraps the caller's OutputStream — do not close it here.
		@SuppressWarnings("resource")
		BinaryWriter w = new BinaryWriter(output);

		// ---- Header (40 bytes) ----
		w.writeAscii(MAGIC);
		w.writeAscii(version);
		w.writeInt(INDEX_TABLE_ADDR);
		w.writeInt(0); // ExtensionDataStartAddress = 0 (no extension)
		w.writePadding(24); // reserved

		// ---- AppInfoBDMV section (38 bytes) ----
		w.writeBytes(appInfo);

		// ---- IndexTable section ----
		w.writeBytes(indexTable);
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private String resolveVersion(IndexBdmv model) {
		String v = model.getVersion();
		return (v != null && !v.isBlank()) ? v : DEFAULT_VERSION;
	}

	/**
	 * Validates that the model is compatible with the target version. Throws {@link WriteException} if an incompatible
	 * combination is found.
	 */
	private void validate(IndexBdmv model, String version) throws IOException {
		if (BDJ_CAPABLE_VERSIONS.contains(version)) {
			return; // all entry types allowed
		}
		// For version "0100" (AVCHD) and any unknown/future version, BD-J is not
		// supported.
		List<IndexBdmv.TitleEntry> all = new java.util.ArrayList<>();
		if (model.getFirstPlayTitle() != null)
			all.add(model.getFirstPlayTitle());
		if (model.getTopMenuTitle() != null)
			all.add(model.getTopMenuTitle());
		if (model.getTitles() != null)
			all.addAll(model.getTitles());

		for (IndexBdmv.TitleEntry entry : all) {
			if (entry.isBdj()) {
				throw new WriteException("Version '" + version + "' does not support BD-J title entries "
						+ "(objectType=2, bdjObjectName='" + entry.getBdjObjectName() + "'). "
						+ "Use version '0200' or '0300' for BD-J discs.");
			}
		}
	}

	/**
	 * Builds the 38-byte AppInfoBDMV section: section_length(4) + reserved(1) + flags(1) + content_provider_name(32).
	 */
	private byte[] buildAppInfoSection(IndexBdmv model) throws IOException {
		ByteArrayBinaryWriter w = new ByteArrayBinaryWriter(4 + APP_INFO_BODY_LEN);

		w.writeInt(APP_INFO_BODY_LEN); // section_length = 34
		w.writePadding(1); // reserved
		w.writeByte(0); // flags (initial_output_mode=0, content_exist=0)

		// content_provider_name: up to 32 ASCII bytes, null-padded
		String name = model.getContentProviderName();
		byte[] nameBytes = (name != null) ? name.getBytes(StandardCharsets.US_ASCII) : new byte[0];
		int writeLen = Math.min(nameBytes.length, PROVIDER_NAME_LEN);
		for (int i = 0; i < writeLen; i++)
			w.writeByte(nameBytes[i] & 0xFF);
		w.writePadding(PROVIDER_NAME_LEN - writeLen);

		return w.toByteArray();
	}

	/**
	 * Builds the IndexTable section (4-byte length prefix + body).
	 */
	private byte[] buildIndexTableSection(IndexBdmv model) throws IOException {
		ByteArrayBinaryWriter wi = new ByteArrayBinaryWriter();

		writeTitleEntry(wi, model.getFirstPlayTitle());
		writeTitleEntry(wi, model.getTopMenuTitle());

		List<IndexBdmv.TitleEntry> titles = model.getTitles() != null ? model.getTitles() : List.of();
		wi.writeShort(titles.size());
		for (IndexBdmv.TitleEntry t : titles) {
			writeTitleEntry(wi, t);
		}

		return wi.toSizePrefixedByteArray();
	}

	/**
	 * Writes a single 12-byte title entry. A {@code null} entry is written as 12 zero bytes (reserved / no-object
	 * slot).
	 */
	private void writeTitleEntry(ByteArrayBinaryWriter w, IndexBdmv.TitleEntry entry) throws IOException {
		if (entry == null) {
			w.writePadding(ENTRY_SIZE);
			return;
		}

		// byte 0: object_type(2 bits:7-6) | access_type(2 bits:5-4) | reserved(4)
		int flagByte = ((entry.getObjectType() & 0x03) << 6) | ((entry.getAccessType() & 0x03) << 4);
		w.writeByte(flagByte);
		w.writePadding(3); // bytes 1-3 reserved

		// byte 4: playback_type(2 bits:7-6) | reserved(6)
		w.writeByte((entry.getPlaybackType() & 0x03) << 6);
		w.writePadding(1); // byte 5 reserved

		// bytes 6-10: type-specific payload (5 bytes)
		if (entry.isBdj()) {
			// 5-char BD-J object name, space-padded on the right if shorter
			String name = entry.getBdjObjectName() != null ? entry.getBdjObjectName() : "";
			byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
			for (int i = 0; i < 5; i++) {
				w.writeByte(i < nameBytes.length ? nameBytes[i] & 0xFF : ' ');
			}
		} else {
			// HDMV: hdmv_object_id (2 bytes big-endian) + 3 reserved bytes
			w.writeShort(entry.getHdmvObjectId());
			w.writePadding(3);
		}

		w.writePadding(1); // byte 11 reserved
	}

}
