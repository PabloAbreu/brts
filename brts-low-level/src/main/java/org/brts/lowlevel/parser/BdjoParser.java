package org.brts.lowlevel.parser;

import org.brts.common.exception.ParseException;
import org.brts.common.io.BinaryReader;
import org.brts.lowlevel.model.bdmv.Bdjo;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@code BDMV/BDJO/XXXXX.bdjo} — BD-J Object files.
 * <p>
 * Binary layout (all integers big-endian): <pre>
 * File header (48 bytes):
 *   magic                              : 4 bytes  ("BDJO")
 *   version                            : 4 bytes  ("0200")
 *   TerminalInfoStartAddress           : 4 bytes
 *   AppCacheInfoStartAddress           : 4 bytes
 *   AccessiblePlaylistsStartAddress    : 4 bytes
 *   AppManagementTableStartAddress     : 4 bytes
 *   KeyInterestTableStartAddress       : 4 bytes
 *   FileAccessInfoStartAddress         : 4 bytes
 *   reserved                           : 16 bytes
 * </pre> Sections follow sequentially (addresses are informational).
 * <p>
 * Reference: libbluray {@code bdjo_parse.c}.
 */
@Slf4j
public class BdjoParser implements BinaryParser<Bdjo> {

	private static final String MAGIC = "BDJO";

	private static final String VERSION_200 = "0200";

	@Override
	public Bdjo parse(InputStream input) throws IOException {
		try (BinaryReader r = new BinaryReader(input)) {
			return readBdjo(r);
		}
	}

	private Bdjo readBdjo(BinaryReader r) throws IOException {
		// ---- File header (48 bytes) ----
		String magic = r.readAscii(4);
		if (!MAGIC.equals(magic)) {
			throw new ParseException("Not a BDJO file: expected '" + MAGIC + "', got '" + magic + "'");
		}
		String version = r.readAscii(4);
		if (!VERSION_200.equals(version)) {
			throw new ParseException("Unsupported BDJO version: '" + version + "'");
		}

		// 6 section addresses (informational, we parse sequentially)
		r.readUnsignedInt(); // TerminalInfoStartAddress
		r.readUnsignedInt(); // AppCacheInfoStartAddress
		r.readUnsignedInt(); // AccessiblePlaylistsStartAddress
		r.readUnsignedInt(); // AppManagementTableStartAddress
		r.readUnsignedInt(); // KeyInterestTableStartAddress
		r.readUnsignedInt(); // FileAccessInfoStartAddress
		r.skip(16); // reserved

		Bdjo bdjo = new Bdjo();
		bdjo.setVersion(version);

		// ---- Sections (sequential) ----
		bdjo.setTerminalInfo(readTerminalInfo(r));
		bdjo.setAppCacheInfo(readAppCacheInfo(r));
		bdjo.setAccessiblePlaylists(readAccessiblePlaylists(r));
		bdjo.setApplications(readAppManagementTable(r));
		bdjo.setKeyInterestTable(readKeyInterestTable(r));
		bdjo.setFileAccessInfo(readFileAccessInfo(r));

		return bdjo;
	}

	// =====================================================================
	// TerminalInfo
	// =====================================================================

	/**
	 * Reads the TerminalInfo section. <pre>
	 *   section_length     : 4 bytes
	 *   default_font       : 5 bytes ASCII
	 *   flags byte         : initial_havi_config(4 bits) | menu_call_mask(1) | title_search_mask(1) | padding(2)
	 *   padding            : remaining bytes to fill section_length
	 * </pre>
	 */
	private Bdjo.TerminalInfo readTerminalInfo(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		log.debug("TerminalInfo section_length: {}", sectionLength);

		Bdjo.TerminalInfo ti = new Bdjo.TerminalInfo();
		ti.setDefaultFont(r.readAscii(5));

		int flags = r.readUnsignedByte();
		ti.setInitialHaviConfigId((flags >> 4) & 0x0F);
		ti.setMenuCallMask(((flags >> 3) & 1) != 0);
		ti.setTitleSearchMask(((flags >> 2) & 1) != 0);

		// Read remaining padding bytes (sectionLength - 6 bytes consumed)
		long remaining = sectionLength - 6;
		if (remaining > 0) {
			ti.setRawPadding(r.readBytes((int) remaining));
		}
		return ti;
	}

	// =====================================================================
	// AppCacheInfo
	// =====================================================================

	/**
	 * Reads the AppCacheInfo section. <pre>
	 *   section_length : 4 bytes
	 *   num_items      : 1 byte
	 *   padding        : 1 byte
	 *   for each item:
	 *     type         : 1 byte (1=JAR, 2=directory)
	 *     ref_to_name  : 5 bytes ASCII
	 *     lang_code    : 3 bytes ASCII
	 *     padding      : 3 bytes
	 * </pre>
	 */
	private Bdjo.AppCacheInfo readAppCacheInfo(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		log.debug("AppCacheInfo section_length: {}", sectionLength);

		Bdjo.AppCacheInfo aci = new Bdjo.AppCacheInfo();

		if (sectionLength < 2) {
			aci.setItems(List.of());
			if (sectionLength > 0)
				r.skip(sectionLength);
			return aci;
		}

		int numItems = r.readUnsignedByte();
		r.skip(1); // padding

		List<Bdjo.AppCacheItem> items = new ArrayList<>(numItems);
		for (int i = 0; i < numItems; i++) {
			Bdjo.AppCacheItem item = new Bdjo.AppCacheItem();
			item.setType(r.readUnsignedByte());
			item.setRefToName(r.readAscii(5));
			item.setLanguageCode(r.readAscii(3));
			r.skip(3); // padding (24 bits)
			items.add(item);
		}
		aci.setItems(items);
		return aci;
	}

	// =====================================================================
	// AccessiblePlaylists
	// =====================================================================

	/**
	 * Reads the AccessiblePlaylists section. <pre>
	 *   section_length               : 4 bytes
	 *   num_pl(11 bits) | access_to_all(1) | autostart_first(1) | padding(19 bits)  : 4 bytes
	 *   for each playlist:
	 *     name     : 5 bytes ASCII
	 *     padding  : 1 byte
	 * </pre>
	 */
	private Bdjo.AccessiblePlaylists readAccessiblePlaylists(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		log.debug("AccessiblePlaylists section_length: {}", sectionLength);

		Bdjo.AccessiblePlaylists ap = new Bdjo.AccessiblePlaylists();

		if (sectionLength < 4) {
			ap.setPlaylistNames(List.of());
			if (sectionLength > 0)
				r.skip(sectionLength);
			return ap;
		}

		long flags32 = r.readUnsignedInt();
		int numPl = (int) ((flags32 >> 21) & 0x7FF);
		ap.setAccessToAllFlag(((flags32 >> 20) & 1) != 0);
		ap.setAutostartFirstPlaylistFlag(((flags32 >> 19) & 1) != 0);

		List<String> names = new ArrayList<>(numPl);
		for (int i = 0; i < numPl; i++) {
			names.add(r.readAscii(5));
			r.skip(1); // padding
		}
		ap.setPlaylistNames(names);
		return ap;
	}

	// =====================================================================
	// Application Management Table
	// =====================================================================

	/**
	 * Reads the Application Management Table. <pre>
	 *   section_length : 4 bytes
	 *   num_apps       : 1 byte
	 *   padding        : 1 byte
	 *   for each app   : variable-length application entry
	 * </pre>
	 */
	private List<Bdjo.BdjoApp> readAppManagementTable(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		log.debug("AppManagementTable section_length: {}", sectionLength);

		if (sectionLength < 2) {
			if (sectionLength > 0)
				r.skip(sectionLength);
			return List.of();
		}

		int numApps = r.readUnsignedByte();
		r.skip(1); // padding
		log.debug("AppManagementTable num_apps: {}", numApps);

		List<Bdjo.BdjoApp> apps = new ArrayList<>(numApps);
		for (int i = 0; i < numApps; i++) {
			apps.add(readBdjoApp(r));
		}
		return apps;
	}

	/**
	 * Reads a single BD-J application entry.
	 * <p>
	 * Reference: libbluray {@code _parse_bdjo_app()}. <pre>
	 *   control_code(8) | type(4) | padding(4) | org_id(32) | app_id(16) : 8 bytes
	 *   descriptor_tag_and_length (10 bytes, skipped)
	 *   num_profile(4 bits) | padding(12 bits) : 2 bytes
	 *   profiles[num_profile] : 6 bytes each (profile_num(16) + major(8) + minor(8) + micro(8) + pad(8))
	 *   priority(8) | binding(2) | visibility(2) | padding(4) : 2 bytes
	 *   app_names...
	 *   icon_locator (length-prefixed string, word-aligned)
	 *   icon_flags(16)
	 *   base_dir (length-prefixed string, word-aligned)
	 *   classpath_extension (length-prefixed string, word-aligned)
	 *   initial_class (length-prefixed string, word-aligned)
	 *   app_params...
	 * </pre>
	 */
	private Bdjo.BdjoApp readBdjoApp(BinaryReader r) throws IOException {
		Bdjo.BdjoApp app = new Bdjo.BdjoApp();

		// control_code(8) + type(4) + padding(4)
		app.setControlCode(r.readUnsignedByte());
		int typeByte = r.readUnsignedByte();
		app.setType((typeByte >> 4) & 0x0F);

		// org_id(32) + app_id(16)
		app.setOrganizationId(r.readUnsignedInt());
		app.setApplicationId(r.readUnsignedShort());

		// Read descriptor tag and length (80 bits = 10 bytes)
		app.setDescriptorHeader(r.readBytes(10));

		// num_profile(4 bits) + padding(12 bits)
		int profileWord = r.readUnsignedShort();
		int numProfiles = (profileWord >> 12) & 0x0F;

		List<Bdjo.AppProfile> profiles = new ArrayList<>(numProfiles);
		for (int p = 0; p < numProfiles; p++) {
			Bdjo.AppProfile prof = new Bdjo.AppProfile();
			prof.setProfileNumber(r.readUnsignedShort());
			prof.setMajorVersion(r.readUnsignedByte());
			prof.setMinorVersion(r.readUnsignedByte());
			prof.setMicroVersion(r.readUnsignedByte());
			r.skip(1); // padding
			profiles.add(prof);
		}
		app.setProfiles(profiles);

		// priority(8) + binding(2) + visibility(2) + padding(4)
		app.setPriority(r.readUnsignedByte());
		int bindVisByte = r.readUnsignedByte();
		app.setBinding((bindVisByte >> 6) & 0x03);
		app.setVisibility((bindVisByte >> 4) & 0x03);

		// Application names
		app.setNames(readAppNames(r));

		// icon_locator (word-aligned length-prefixed string)
		app.setIconLocator(readAppString(r));

		// icon_flags (16 bits)
		app.setIconFlags(r.readUnsignedShort());

		// base_dir, classpath_extension, initial_class
		app.setBaseDir(readAppString(r));
		app.setClasspathExtension(readAppString(r));
		app.setInitialClass(readAppString(r));

		// Application parameters
		app.setParameters(readAppParams(r));

		log.debug("App: controlCode={}, type={}, orgId=0x{}, appId=0x{}, class={}", app.getControlCode(), app.getType(),
				Long.toHexString(app.getOrganizationId()), Integer.toHexString(app.getApplicationId()),
				app.getInitialClass());

		return app;
	}

	/**
	 * Reads the application names block. <pre>
	 *   data_length(16 bits)
	 *   for each name: lang(3 bytes) + name_length(1 byte) + name(name_length bytes)
	 *   word-align padding if data_length is odd
	 * </pre>
	 */
	private List<Bdjo.AppName> readAppNames(BinaryReader r) throws IOException {
		int dataLength = r.readUnsignedShort();
		List<Bdjo.AppName> names = new ArrayList<>();

		int bytesRead = 0;
		while (bytesRead < dataLength) {
			Bdjo.AppName name = new Bdjo.AppName();
			name.setLanguage(r.readAscii(3));
			int nameLen = r.readUnsignedByte();
			name.setName(r.readAscii(nameLen));
			bytesRead += 3 + 1 + nameLen;
			names.add(name);
		}

		// Word-align: skip 1 byte if dataLength is odd
		if ((dataLength & 1) != 0) {
			r.skip(1);
		}
		return names;
	}

	/**
	 * Reads a word-aligned length-prefixed string (as in libbluray
	 * {@code _read_app_string}). <pre>
	 *   length(8 bits) + string(length bytes) + padding(1 byte if length is even)
	 * </pre>
	 */
	private String readAppString(BinaryReader r) throws IOException {
		int length = r.readUnsignedByte();
		String s = length > 0 ? r.readAscii(length) : "";
		// Word-align: if length is even (length+1 total bytes is odd), add 1 pad byte
		if ((length & 1) == 0) {
			r.skip(1);
		}
		return s;
	}

	/**
	 * Reads the application parameters block. <pre>
	 *   data_length(8 bits)
	 *   for each param: param_length(8 bits) + param(param_length bytes)
	 *   word-align padding if data_length is even (i.e. total odd)
	 * </pre>
	 */
	private List<String> readAppParams(BinaryReader r) throws IOException {
		int dataLength = r.readUnsignedByte();
		List<String> params = new ArrayList<>();

		int bytesRead = 0;
		while (bytesRead < dataLength) {
			int paramLen = r.readUnsignedByte();
			params.add(r.readAscii(paramLen));
			bytesRead += 1 + paramLen;
		}

		// Word-align: if data_length is even (data_length+1 total bytes is odd), add 1
		// pad byte
		if ((dataLength & 1) == 0) {
			r.skip(1);
		}
		return params;
	}

	// =====================================================================
	// KeyInterestTable
	// =====================================================================

	/**
	 * Reads the Key Interest Table (4 bytes = 32 bits). <pre>
	 *   11 single-bit flags + 21 bits padding
	 * </pre>
	 */
	private Bdjo.KeyInterestTable readKeyInterestTable(BinaryReader r) throws IOException {
		Bdjo.KeyInterestTable kit = new Bdjo.KeyInterestTable();

		int b0 = r.readUnsignedByte();
		kit.setVkPlay((b0 & 0x80) != 0);
		kit.setVkStop((b0 & 0x40) != 0);
		kit.setVkFfw((b0 & 0x20) != 0);
		kit.setVkRew((b0 & 0x10) != 0);
		kit.setVkTrackNext((b0 & 0x08) != 0);
		kit.setVkTrackPrev((b0 & 0x04) != 0);
		kit.setVkPause((b0 & 0x02) != 0);
		kit.setVkStillOff((b0 & 0x01) != 0);

		int b1 = r.readUnsignedByte();
		kit.setVkSecAudioEnaDis((b1 & 0x80) != 0);
		kit.setVkSecVideoEnaDis((b1 & 0x40) != 0);
		kit.setPgTextstEnaDis((b1 & 0x20) != 0);

		r.skip(2); // remaining 21 bits padding (fits in 2 bytes with the 5 consumed bits)
		return kit;
	}

	// =====================================================================
	// FileAccessInfo
	// =====================================================================

	/**
	 * Reads the File Access Info section. <pre>
	 *   file_access_length(16 bits) + path(file_access_length bytes)
	 * </pre>
	 */
	private String readFileAccessInfo(BinaryReader r) throws IOException {
		int length = r.readUnsignedShort();
		return length > 0 ? r.readAscii(length) : "";
	}

}
