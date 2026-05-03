package org.brts.lowlevel.titlemenu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Explicit directional navigation overrides for a title menu button.
 * <p>
 * Each field holds the zero-based index of the target title entry in the direction indicated, or {@code null} to let
 * the auto-wiring logic fill that direction based on grid position.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavigationOverride {

	/** Index of the button to focus when the user presses Up, or null for auto. */
	private Integer up;

	/** Index of the button to focus when the user presses Down, or null for auto. */
	private Integer down;

	/** Index of the button to focus when the user presses Left, or null for auto. */
	private Integer left;

	/** Index of the button to focus when the user presses Right, or null for auto. */
	private Integer right;

}
