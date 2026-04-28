package org.brts.lowlevel.model.bdmv;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model for {@code BDMV/BDJO/XXXXX.bdjo} — a BD-J Object file.
 * <p>
 * A BDJO file describes the BD-J application(s) that are launched when a BD-J title is entered. It contains:
 * <ul>
 * <li>Terminal information (default font, HAVi configuration, call masks)</li>
 * <li>Application cache information (JAR references)</li>
 * <li>Accessible playlists (which playlists the app may play)</li>
 * <li>Application management table (the Xlet applications to run)</li>
 * <li>Key interest table (which remote control keys the app listens to)</li>
 * <li>File access information (VFS paths the app may access)</li>
 * </ul>
 * <p>
 * Reference: libbluray {@code bdjo_data.h} / {@code bdjo_parse.c}.
 */
@Getter
@Setter
public class Bdjo {

	/** BDJO version string (e.g. "0200"). */
	private String version = "0200";

	/** Terminal information section. */
	private TerminalInfo terminalInfo = new TerminalInfo();

	/** Application cache information section. */
	private AppCacheInfo appCacheInfo = new AppCacheInfo();

	/** Accessible playlists section. */
	private AccessiblePlaylists accessiblePlaylists = new AccessiblePlaylists();

	/** Application management table. */
	private List<BdjoApp> applications;

	/** Key interest table. */
	private KeyInterestTable keyInterestTable = new KeyInterestTable();

	/** File access information. */
	private String fileAccessInfo = "";

	// =========================================================================
	// TerminalInfo
	// =========================================================================

	/**
	 * Terminal information — default font, HAVi config, call masks.
	 * <p>
	 * Binary layout (after 4-byte section_length):
	 *
	 * <pre>
	 *   default_font          : 5 bytes ASCII ("*****" = none)
	 *   initial_havi_config   : 4 bits
	 *   menu_call_mask        : 1 bit
	 *   title_search_mask     : 1 bit
	 *   padding               : 34 bits (remainder of 10-byte body)
	 * </pre>
	 */
	@Getter
	@Setter
	public static class TerminalInfo {

		/**
		 * Default AWT font file name (5 chars, references AUXDATA/xxxxx.otf). "*****" = none.
		 */
		private String defaultFont = "*****";

		/** Initial HAVi configuration ID (4 bits). */
		private int initialHaviConfigId;

		/** If true, menu call is masked (disabled) for this title. */
		private boolean menuCallMask;

		/** If true, title search is masked (disabled) for this title. */
		private boolean titleSearchMask;

		/**
		 * Raw padding bytes from the TerminalInfo section (4 bytes after the flags byte). Preserved for byte-for-byte
		 * round-trip fidelity.
		 */
		private byte[] rawPadding = new byte[4];

	}

	// =========================================================================
	// AppCacheInfo
	// =========================================================================

	/**
	 * Application cache information — lists JARs/directories to cache.
	 */
	@Getter
	@Setter
	public static class AppCacheInfo {

		private List<AppCacheItem> items;

	}

	/**
	 * A single application cache entry.
	 */
	@Getter
	@Setter
	public static class AppCacheItem {

		/** Cache entry type: 1 = JAR file, 2 = directory. */
		private int type = 1;

		/** Reference name (5 chars): JAR/xxxxx.jar or JAR/xxxxx/. */
		private String refToName = "00000";

		/** Language code (3 chars). "*.*" = always cache. "%%%" = fallback. */
		private String languageCode = "*.*";

	}

	// =========================================================================
	// AccessiblePlaylists
	// =========================================================================

	/**
	 * Accessible playlists section.
	 */
	@Getter
	@Setter
	public static class AccessiblePlaylists {

		/** If true, the app has access to all playlists on the disc. */
		private boolean accessToAllFlag;

		/** If true, the first accessible playlist is auto-started. */
		private boolean autostartFirstPlaylistFlag;

		/** Explicit playlist names (5-char, references PLAYLIST/xxxxx.mpls). */
		private List<String> playlistNames;

	}

	// =========================================================================
	// BdjoApp (Application)
	// =========================================================================

	/**
	 * A BD-J application entry in the application management table.
	 */
	@Getter
	@Setter
	public static class BdjoApp {

		/** Control code: 1 = autostart, 2 = present (started by other app). */
		private int controlCode = 1;

		/** Application type: 1 = BD-J App. */
		private int type = 1;

		/** Organization ID (32-bit). */
		private long organizationId;

		/** Application ID (16-bit). */
		private int applicationId;

		/**
		 * Raw 10-byte descriptor tag+length header. Preserved for byte-for-byte round-trip fidelity. When writing, if
		 * null, the header is computed from the descriptor body length.
		 */
		private byte[] descriptorHeader;

		/** Application profiles. */
		private List<AppProfile> profiles;

		/** Application priority (0–255). */
		private int priority;

		/**
		 * Binding type: 0 = unbound, 1 = disc bound, 3 = title bound.
		 */
		private int binding;

		/**
		 * Visibility: 0 = none, 1 = visible to other apps, 2 = visible to user.
		 */
		private int visibility;

		/** Application names (localized). */
		private List<AppName> names;

		/** Icon locator (relative to base_dir). */
		private String iconLocator = "";

		/** Icon flags (16 bits). */
		private int iconFlags;

		/** Base directory: "00000" → 00000.jar!/. */
		private String baseDir = "";

		/** Classpath extension (separator ";"). */
		private String classpathExtension = "";

		/** Fully-qualified initial class name. */
		private String initialClass = "";

		/** Application parameters. */
		private List<String> parameters;

	}

	/**
	 * Application profile entry.
	 */
	@Getter
	@Setter
	public static class AppProfile {

		/** Profile number: 1 = BD-ROM profile 1, 2 = BD-ROM profile 2. */
		private int profileNumber = 1;

		private int majorVersion;

		private int minorVersion;

		private int microVersion;

	}

	/**
	 * Localised application name.
	 */
	@Getter
	@Setter
	public static class AppName {

		/** ISO 639-2 language code (3 chars). */
		private String language = "eng";

		/** Human-readable application name (UTF-8). */
		private String name = "";

	}

	// =========================================================================
	// KeyInterestTable
	// =========================================================================

	/**
	 * Key interest table — which remote-control keys the application listens to.
	 * <p>
	 * Binary layout: 11 single-bit flags followed by 21 bits of padding (4 bytes total).
	 */
	@Getter
	@Setter
	public static class KeyInterestTable {

		private boolean vkPlay;

		private boolean vkStop;

		private boolean vkFfw;

		private boolean vkRew;

		private boolean vkTrackNext;

		private boolean vkTrackPrev;

		private boolean vkPause;

		private boolean vkStillOff;

		private boolean vkSecAudioEnaDis;

		private boolean vkSecVideoEnaDis;

		private boolean pgTextstEnaDis;

	}

}
