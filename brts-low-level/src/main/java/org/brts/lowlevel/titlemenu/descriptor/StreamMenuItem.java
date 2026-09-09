package org.brts.lowlevel.titlemenu.descriptor;

import org.brts.common.menu.TextStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * An audio or subtitle track selection item for the title menu's embedded settings submenu.
 * <p>
 * The stream number refers to the same physical track position across all titles' source media. Audio stream numbers
 * are 1-based; subtitle stream number {@code 0} means subtitles off.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamMenuItem {

	/** Human-readable label for the button. */
	private String description;

	/** Physical audio or subtitle stream number. */
	private int streamNumber;

	/** Optional style override merged over the title menu's global style. */
	private TextStyle style;

}