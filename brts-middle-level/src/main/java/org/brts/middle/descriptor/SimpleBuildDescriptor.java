package org.brts.middle.descriptor;

import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Simplified middle-level descriptor for a single-title disc with no top menu.
 * <p>
 * This flattens {@link DiscDescriptor} and its single {@link TitleDescriptor} into one JSON shape, keeping disc-wide
 * styling/popup/PGS capabilities while dropping multi-title and top-menu concerns. Example
 * {@code simple-build-descriptor.json}:
 *
 * <pre>{@code
 * {
 *   "discName": "My Movie",
 *   "sourceMkv": "/videos/movie.mkv",
 *   "audioLanguages": ["eng"],
 *   "subtitleLanguages": ["eng"],
 *   "chapters": [{ "timeSeconds": 0 }, { "timeSeconds": 3600 }]
 * }
 * }</pre>
 */
@Getter
@Setter
public class SimpleBuildDescriptor {

	private String discName;
	private String outputFolder;// base folder for the BD output under discName

	/** Global text style for all disc menus (e.g. popup menu). */
	private TextStyle style;

	/** Popup menu style override, merged over {@link #style}. */
	private TextStyle popupStyle;

	/** Popup menu presentation configuration (layout, dimensions, style and backgrounds). */
	private PopupMenuConfig popupMenu;

	/** PGS subtitle rendering configuration applied during conversion. */
	private PgsRenderConfig pgsConfig;

	/** Optional display name for the title. Falls back to source MKV filename if absent. */
	private String displayName;

	/** Path to the source MKV file. */
	private String sourceMkv;

	/**
	 * Ordered list of ISO 639-2 language codes for audio tracks to include. If null or empty, all audio tracks in the
	 * MKV are included.
	 */
	private List<String> audioLanguages;

	/**
	 * Ordered list of ISO 639-2 language codes for subtitle (PG) tracks to include. If null or empty, all PG tracks in
	 * the MKV are included.
	 */
	private List<String> subtitleLanguages;

	/** Chapter markers expressed as seconds from stream start. */
	private List<TitleDescriptor.ChapterMarker> chapters;

	/**
	 * Controls popup menu generation for the title. Defaults to {@link PopupMenuMode#AUTO} which generates a popup menu
	 * if the title has more than one audio or more than one subtitle track.
	 */
	private PopupMenuMode popupMenuMode = PopupMenuMode.AUTO;

	/**
	 * Builds the equivalent single-title, no-top-menu {@link DiscDescriptor} for orchestration.
	 */
	public DiscDescriptor toDiscDescriptor() {
		TitleDescriptor title = new TitleDescriptor();
		title.setTitleId(1);
		title.setDisplayName(displayName);
		title.setSourceMkv(sourceMkv);
		title.setAudioLanguages(audioLanguages);
		title.setSubtitleLanguages(subtitleLanguages);
		title.setChapters(chapters);
		title.setPopupMenu(popupMenuMode);

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName(discName);
		disc.setHasTopMenu(false);
		disc.setTitleMenuConfig(null);
		disc.setStyle(style);
		disc.setPopupStyle(popupStyle);
		disc.setPopupMenu(popupMenu);
		disc.setPgsConfig(pgsConfig);
		disc.setTitles(List.of(title));
		disc.setOutputFolder(outputFolder); // FIXME allow user to specify output folder

		return disc;
	}

}
