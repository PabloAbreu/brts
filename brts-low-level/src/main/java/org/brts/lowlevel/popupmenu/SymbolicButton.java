package org.brts.lowlevel.popupmenu;

import java.util.List;

import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

/**
 * A button description before rendering: label text and navigation behavior, with no image/style concerns yet.
 */
public record SymbolicButton(String text, List<NavigationCommand> commands, boolean autoAction) {

	public SymbolicButton(String text, List<NavigationCommand> commands) {
		this(text, commands, false);
	}
}
