package org.brts.middle.menu.descriptor;

/**
 * Enumeration of miscellaneous menu item types.
 * <p>
 * Each type maps to a specific Blu-ray HDMV navigation command:
 * <ul>
 *   <li>{@link #LAUNCH} → {@code PLAY_PL} (play a playlist)</li>
 *   <li>{@link #GO_BACK} → {@code JUMP_TITLE} (jump to a title, typically the top menu)</li>
 *   <li>{@link #POPUP_OFF} → {@code POPUP_OFF} (dismiss the pop-up menu)</li>
 *   <li>{@link #RESUME} → {@code RESUME} (resume from saved location)</li>
 * </ul>
 */
public enum MiscMenuItemType {

    /** Launch playback of a movie/playlist. */
    LAUNCH,

    /** Navigate back to a previous menu (title). */
    GO_BACK,

    /** Dismiss the pop-up menu overlay. */
    POPUP_OFF,

    /** Resume playback from the last saved position. */
    RESUME
}
