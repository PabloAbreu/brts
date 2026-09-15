package org.brts.lowlevel.mkv;

import java.util.Set;

import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON descriptor for the {@code mkv-to-playlist} CLI command, grouping all conversion input options.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkvToPlaylistDescriptor {

	/** Path to the source MKV file. */
	private String input;

	/** 5-digit clip name for the generated M2TS/CLPI/MPLS. */
	private String clipName = "00001";

	/** MKV track numbers for audio to include. Null or empty means all audio tracks. */
	private Set<Integer> audioTracks;

	/** MKV track numbers for subtitles to include. Null or empty means all subtitle tracks. */
	private Set<Integer> subtitleTracks;

	/** PGS subtitle rendering configuration. Null falls back to defaults. */
	private PgsRenderConfig pgsConfig;

	/**
	 * Popup menu presentation configuration; a non-null value enables popup menu generation. Track-entry lists are
	 * ignored here since they are computed from the selected MKV tracks at conversion time.
	 */
	private PopupMenuConfig popupMenu;

}
