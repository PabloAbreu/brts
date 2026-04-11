package org.brts.middle.menu.descriptor;

import lombok.Getter;
import lombok.Setter;

/**
 * An audio track selection menu item.
 * <p>
 * Each audio item corresponds to one audio stream the user can select.
 *
 * <h2>Example</h2> <pre>{@code
 * {
 *   "description": "English DTS-HD",
 *   "streamNumber": 1,
 *   "style": { "normalColor": "#FFFFFF00" }
 * }
 * }</pre>
 */
@Getter
@Setter
public class AudioMenuItem extends MenuItem {

	/**
	 * 1-based audio stream number. Maps to PSR1 (primary audio stream number) in Blu-ray
	 * navigation.
	 */
	private int streamNumber;

}
