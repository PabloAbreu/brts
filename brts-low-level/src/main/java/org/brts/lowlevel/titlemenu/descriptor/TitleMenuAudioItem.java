package org.brts.lowlevel.titlemenu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * An audio track selection item for the title menu's embedded settings submenu.
 * <p>
 * Selecting this button does not switch the menu's own audio; it writes the chosen stream index to
 * {@code NavigationCommandUtils.GPR_AUDIO_CHOICE}, later read by each title's MovieObject to select its own matching
 * stream. The stream number must therefore refer to the same physical track position across all titles' source media.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleMenuAudioItem {

	/** Human-readable label for the button. */
	private String description;

	/** 1-based audio stream number, consistent across all titles' source media. */
	private int streamNumber;

}
