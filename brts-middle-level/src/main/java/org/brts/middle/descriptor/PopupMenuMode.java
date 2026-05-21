package org.brts.middle.descriptor;

/**
 * Toggle controlling popup menu generation for a title.
 * <ul>
 * <li>{@link #AUTO} — generate a popup menu if the title has more than one audio or more than one subtitle track</li>
 * <li>{@link #TRUE} — always generate a popup menu</li>
 * <li>{@link #FALSE} — never generate a popup menu</li>
 * </ul>
 */
public enum PopupMenuMode {

	AUTO, TRUE, FALSE

}
