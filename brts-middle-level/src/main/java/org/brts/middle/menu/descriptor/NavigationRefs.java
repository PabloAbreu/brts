package org.brts.middle.menu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Explicit directional navigation overrides for a single menu item.
 * <p>
 * Each field holds the {@code id} of the target item in the direction indicated, or
 * {@code null} to let the auto-wiring logic fill that direction.
 *
 * <h2>Example</h2> <pre>{@code
 * "nav": {
 *   "down": "sub-en",
 *   "up":   "misc-play"
 * }
 * }</pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavigationRefs {

	/** Id of the button to focus when the user presses Up. */
	private String up;

	/** Id of the button to focus when the user presses Down. */
	private String down;

	/** Id of the button to focus when the user presses Left. */
	private String left;

	/** Id of the button to focus when the user presses Right. */
	private String right;

}
