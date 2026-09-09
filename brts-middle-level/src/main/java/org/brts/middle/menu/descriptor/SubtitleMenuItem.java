package org.brts.middle.menu.descriptor;

import lombok.Getter;
import lombok.Setter;

/**
 * A subtitle track selection menu item.
 * <p>
 * Each subtitle item corresponds to one PG/text subtitle stream. {@link #getStreamNumber()} maps to PSR2 (PG/subtitle
 * stream number) in Blu-ray navigation. A value of 0 means "subtitles off" (0x1FFF = no stream).
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * {
 *   "description": "English Subtitles",
 *   "streamNumber": 1
 * }
 * }</pre>
 */
@Getter
@Setter
public class SubtitleMenuItem extends StreamMenuItem {

}
