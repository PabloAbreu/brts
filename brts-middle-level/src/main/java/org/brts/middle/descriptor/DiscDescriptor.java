package org.brts.middle.descriptor;

import org.brts.common.menu.TextStyle;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Middle-level descriptor for a complete Blu-ray disc.
 * <p>
 * This is the main input for the middle-level authoring pipeline. Example {@code disc.json}:
 *
 * <pre>{@code
 * {
 *   "discName": "My Movie",
 *   "hasTopMenu": false,
 *   "titles": [
 *     {
 *       "titleId": 1,
 *       "sourceMkv": "/videos/movie.mkv",
 *       "audioLanguages": ["eng"],
 *       "chapters": [{ "timeSeconds": 0 }, { "timeSeconds": 3600 }]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class DiscDescriptor {

	private String discName;

	private String outputFolder;// for the BD

	/** If true, a top menu object will be generated (requires menu title config). */
	private boolean hasTopMenu = false;

	/**
	 * Optional title menu configuration. When non-null and the disc has more than one title, a selectable title menu is
	 * generated and wired as First Play and Top Menu.
	 */
	private TitleMenuConfig titleMenuConfig;

	/**
	 * Global text style for all disc menus (title menu and popup menus). Serves as the base style that more specific
	 * overrides (title menu style, popup style) are merged over. When {@code null}, defaults are resolved from
	 * {@code textStyle.*} properties in brts.conf at the lowest layer.
	 */
	private TextStyle style;

	/**
	 * Disc-wide popup menu style override. When non-null, merged over {@link #style} to produce the effective style for
	 * all popup menus on this disc. When {@code null}, the global {@link #style} (or brts.conf defaults) is used.
	 */
	private TextStyle popupStyle;

	private List<TitleDescriptor> titles;

}
