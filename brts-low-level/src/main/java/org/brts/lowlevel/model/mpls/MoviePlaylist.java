package org.brts.lowlevel.model.mpls;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model for an MPLS (Movie Playlist) file — {@code BDMV/PLAYLIST/XXXXX.mpls}.
 * <p>
 * An MPLS file describes:
 * <ul>
 * <li>An ordered list of {@link PlayItem}s, each referencing an M2TS clip</li>
 * <li>A list of {@link SubPath}s for secondary streams (audio, subtitles, menus shown during main playback) —
 * simplified support only</li>
 * <li>Chapter marks ({@link PlayMark}s)</li>
 * <li>Playback conditions (UI state, mnu flag, etc.)</li>
 * </ul>
 */
@Getter
@Setter
public class MoviePlaylist {

	/** Playlist number (5-digit, e.g. "00001"). */
	private String playlistName;

	/** If true, the playlist represents a menu (IG stream drives navigation). */
	@JsonProperty("isMenu")
	private boolean isMenu = false;

	/** Ordered list of play items, each referencing an M2TS clip. */
	private List<PlayItem> playItems;

	/** List of sub-paths for secondary streams (audio, subtitles, menus shown during main playback). */
	private List<SubPath> subPaths;

	/** List of chapter marks. */
	private List<PlayMark> playMarks;

}
