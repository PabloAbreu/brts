package org.brts.middle.menu.descriptor;

import lombok.Getter;
import lombok.Setter;

/**
 * A miscellaneous navigation menu item (launch movie, go back, resume, etc.).
 *
 * <h2>Example — launch a movie</h2> <pre>{@code
 * {
 *   "type": "LAUNCH",
 *   "target": "00001",
 *   "description": "Lancer le film",
 *   "icon": "/path/to/play-icon.png"
 * }
 * }</pre>
 *
 * <h2>Example — go back to top menu</h2> <pre>{@code
 * {
 *   "type": "GO_BACK",
 *   "target": "0",
 *   "description": "Menu principal"
 * }
 * }</pre>
 */
@Getter
@Setter
public class MiscMenuItem extends MenuItem {

	/** The type of action this button performs. */
	private MiscMenuItemType type;

	/**
	 * Target identifier, interpreted depending on {@link #type}:
	 * <ul>
	 * <li>{@code LAUNCH}: playlist name (e.g. "00001" → MPLS 00001)</li>
	 * <li>{@code GO_BACK}: title number to jump to (e.g. "0" = top menu, "65535" = first
	 * play)</li>
	 * <li>{@code POPUP_OFF}: not used</li>
	 * <li>{@code RESUME}: not used</li>
	 * </ul>
	 */
	private String target;

	/**
	 * Path to a black-and-white PNG icon image. The icon will be loaded and colourised
	 * per button state (normal / selected / activated) to produce the final RLE images.
	 * Optional if {@code description} is provided.
	 */
	private String icon;

}
