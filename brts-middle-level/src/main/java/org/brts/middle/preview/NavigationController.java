package org.brts.middle.preview;

import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simulates Blu-ray remote-control navigation on an IGS display set.
 * <p>
 * Handles directional movement (up / down / left / right), selection (Enter), and numeric
 * input. After each action it updates the {@link DisplaySetPreviewModel} and returns a
 * {@link NavigationResult} describing what changed.
 */
public class NavigationController {

	private static final Logger log = LoggerFactory.getLogger(NavigationController.class);

	private final DisplaySetPreviewModel model;

	public NavigationController(DisplaySetPreviewModel model) {
		this.model = model;
	}

	// ── Public actions ──────────────────────────────────────────────────────

	/** Move selection up. */
	public NavigationResult moveUp() {
		return moveToNeighbour(Direction.UP);
	}

	/** Move selection down. */
	public NavigationResult moveDown() {
		return moveToNeighbour(Direction.DOWN);
	}

	/** Move selection left. */
	public NavigationResult moveLeft() {
		return moveToNeighbour(Direction.LEFT);
	}

	/** Move selection right. */
	public NavigationResult moveRight() {
		return moveToNeighbour(Direction.RIGHT);
	}

	/**
	 * Activate (enter / confirm) the currently selected button. Checks navigation
	 * commands and returns a result describing them.
	 */
	public NavigationResult activate() {
		IgsButton btn = model.getAllButtons().get(model.getSelectedButtonId());
		if (btn == null) {
			return NavigationResult.none("No button selected");
		}

		model.setActivatedButtonId(btn.getId());

		// Decode navigation commands (if any) into a human-readable overlay
		String commandDescription = describeNavigationCommands(btn);
		if (commandDescription != null && !commandDescription.isEmpty()) {
			model.setCommandOverlayMessage(commandDescription);
			model.setCommandOverlayTimestamp(System.currentTimeMillis());
		}

		log.info("Activated button #{}: {}", btn.getId(), commandDescription);
		return new NavigationResult(NavigationResult.Type.ACTIVATED, btn.getId(), commandDescription);
	}

	/**
	 * Clears the activated state (called after the overlay timeout).
	 */
	public void clearActivation() {
		model.setActivatedButtonId(-1);
		model.setCommandOverlayMessage(null);
	}

	/**
	 * Switch to a specific page by id.
	 */
	public NavigationResult goToPage(int pageId) {
		for (int i = 0; i < model.getPages().size(); i++) {
			if (model.getPages().get(i).getId() == pageId) {
				model.setCurrentPageIndex(i);
				model.resetBogState();
				model.resetSelectedButton();
				log.info("Switched to page #{}", pageId);
				return new NavigationResult(NavigationResult.Type.PAGE_CHANGED, pageId, "Page " + pageId);
			}
		}
		return NavigationResult.none("Page " + pageId + " not found");
	}

	// ── Internal navigation ─────────────────────────────────────────────────

	private enum Direction {

		UP, DOWN, LEFT, RIGHT

	}

	private NavigationResult moveToNeighbour(Direction dir) {
		IgsButton current = model.getAllButtons().get(model.getSelectedButtonId());
		if (current == null) {
			return NavigationResult.none("No button selected");
		}

		int targetId = switch (dir) {
			case UP -> current.getUpperButtonIdRef();
			case DOWN -> current.getLowerButtonIdRef();
			case LEFT -> current.getLeftButtonIdRef();
			case RIGHT -> current.getRightButtonIdRef();
		};

		// 0xFFFF means "no neighbour in that direction"
		if (targetId == 0xFFFF || targetId == current.getId()) {
			return NavigationResult.none("No neighbour " + dir);
		}

		// Verify the target button exists and is enabled
		if (!model.getAllButtons().containsKey(targetId)) {
			return NavigationResult.none("Neighbour button " + targetId + " not found");
		}

		// Check the target is enabled in one of the current page's BOGs
		IgsPage page = model.getCurrentPage();
		if (page != null && !isButtonEnabled(page, targetId)) {
			return NavigationResult.none("Button " + targetId + " not enabled");
		}

		model.setSelectedButtonId(targetId);
		log.debug("Moved {} → button #{}", dir, targetId);
		return new NavigationResult(NavigationResult.Type.SELECTION_CHANGED, targetId, dir + " → button #" + targetId);
	}

	private boolean isButtonEnabled(IgsPage page, int buttonId) {
		for (int i = 0; i < page.getBogs().size(); i++) {
			Integer enabledId = model.getBogEnabledButtons().get(i);
			if (enabledId != null && enabledId == buttonId) {
				return true;
			}
			// Also accept if the button is in this BOG (some menus don't set
			// default-valid properly, treat any button in a BOG as accessible)
			for (IgsButton btn : page.getBogs().get(i).getButtons()) {
				if (btn.getId() == buttonId)
					return true;
			}
		}
		return false;
	}

	// ── Navigation-command description ──────────────────────────────────────

	/**
	 * Provides a human-readable description of a button's navigation commands. This is
	 * displayed as an overlay when the user activates a button. Delegates to
	 * {@link ParsedNavigationCommand} for proper mnemonic decoding.
	 */
	private String describeNavigationCommands(IgsButton btn) {
		List<ParsedNavigationCommand> cmds = btn.getNavigationCommands();
		if (cmds == null || cmds.isEmpty()) {
			return "No navigation commands";
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cmds.size(); i++) {
			ParsedNavigationCommand cmd = cmds.get(i);
			sb.append(String.format("[%d] %s: %s", i, cmd.getMnemonicString(), cmd.describe()));
			if (i < cmds.size() - 1)
				sb.append(" | ");
		}
		return sb.toString();
	}

}
