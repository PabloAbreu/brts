package org.brts.middle.preview;

import java.util.List;
import java.util.OptionalInt;

import org.brts.lowlevel.bdmv.GprState;
import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator.SimulationResult;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand.ButtonPageTarget;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Simulates Blu-ray remote-control navigation on an IGS display set.
 * <p>
 * Handles directional movement (up / down / left / right), selection (Enter), and numeric input. After each action it
 * updates the {@link DisplaySetPreviewModel} and returns a {@link NavigationResult} describing what changed.
 */
@Slf4j
@RequiredArgsConstructor
public class NavigationController {

	// 0xFFFF means "no neighbour in that direction"
	private static final int NO_NEIGHBOUR_ID = 0xFFFF;

	/** Generous bound to avoid hanging the UI on pathological authored GOTO loops. */
	private static final long MAX_SIMULATION_STEPS = 10_000;

	private final DisplaySetPreviewModel model;

	/** Single instance reused across all button activations, sharing one {@link GprState} for the session. */
	private final NavigationCommandSimulator simulator = new NavigationCommandSimulator(null, new GprState(),
			MAX_SIMULATION_STEPS);

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
	 * Activate (enter / confirm) the currently selected button. Executes its navigation commands (via
	 * {@link NavigationCommandSimulator}) and simulates any {@code SET_BUTTON_PAGE} effect, in addition to describing
	 * the commands in the overlay.
	 */
	public NavigationResult activate() {
		IgsButton btn = model.getCurrentButton();
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

		simulateButtonPageEffect(btn);

		log.info("Activated button #{}: {}", btn.getId(), commandDescription);
		return new NavigationResult(NavigationResult.Type.ACTIVATED, btn.getId(), commandDescription);
	}

	/**
	 * Runs the button's navigation commands through the simulator, persists the resulting GPR state, and applies any
	 * resolved {@code SET_BUTTON_PAGE} effect (page switch and/or explicit button selection).
	 */
	private void simulateButtonPageEffect(IgsButton btn) {
		List<NavigationCommand> commands = btn.getNavigationCommands();
		if (commands == null || commands.isEmpty()) {
			return;
		}

		SimulationResult result;
		try {
			result = simulator.run(commands);
			log.debug("GPR state after activation: {}", result.finalGprState());
		} catch (NavigationCommandSimulator.SimulationException e) {
			log.warn("Navigation command simulation failed for button #{}: {}", btn.getId(), e.getMessage());
			return;
		}

		ButtonPageTarget target = resolveLastButtonPageTarget(commands, result);
		if (target == null) {
			return;
		}

		if (target.pageId().isPresent()) {
			OptionalInt buttonId = target.buttonId();
			goToPage(target.pageId().getAsInt(), buttonId);
		} else if (target.buttonId().isPresent()) {
			int buttonId = target.buttonId().getAsInt();
			IgsPage page = model.getCurrentPage();
			if (page != null && isButtonEnabled(page, buttonId)) {
				model.setSelectedButtonId(buttonId);
			} else {
				log.warn("SET_BUTTON_PAGE targeted button #{} which is not enabled on the current page", buttonId);
			}
		}
	}

	private ButtonPageTarget resolveLastButtonPageTarget(List<NavigationCommand> commands, SimulationResult result) {
		ButtonPageTarget last = null;
		for (NavigationCommand cmd : commands) {
			ParsedNavigationCommand parsed = cmd.toParsed();
			if (parsed.getMnemonic() != NavigationCommandMnemonic.SET_BUTTON_PAGE) {
				continue;
			}
			try {
				last = parsed.resolveButtonPageTarget(result.finalGprState());
			} catch (IllegalStateException e) {
				log.warn("Could not resolve SET_BUTTON_PAGE target: {}", e.getMessage());
			}
		}
		return last;
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
		return goToPage(pageId, OptionalInt.empty());
	}

	/**
	 * Switch to a specific page by id, optionally selecting a specific button instead of the page's default.
	 */
	public NavigationResult goToPage(int pageId, OptionalInt explicitButtonId) {
		for (int i = 0; i < model.getPages().size(); i++) {
			if (model.getPages().get(i).getId() == pageId) {
				int oldPageIndex = model.getCurrentPageIndex();
				model.setCurrentPageIndex(i);
				model.resetBogState();
				IgsPage page = model.getCurrentPage();
				if (explicitButtonId.isPresent() && page != null
						&& model.findButtonOnCurrentPage(explicitButtonId.getAsInt()) != null) {
					model.setSelectedButtonId(explicitButtonId.getAsInt());
				} else {
					model.resetSelectedButton();
				}
				log.info("Switched to page ID#{} from page idx {}", pageId, oldPageIndex);
				log.debug("Current page: {} (palette {}, {} BOGs, {} buttons)", page.getId(), page.getPaletteIdRef(),
						page.getBogs().size(), page.getBogs().stream().mapToInt(b -> b.getButtons().size()).sum());
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
		IgsButton current = model.getCurrentButton();
		if (current == null) {
			return NavigationResult.none("No button selected");
		}

		int targetId = switch (dir) {
		case UP -> current.getUpperButtonIdRef();
		case DOWN -> current.getLowerButtonIdRef();
		case LEFT -> current.getLeftButtonIdRef();
		case RIGHT -> current.getRightButtonIdRef();
		};

		if (targetId == NO_NEIGHBOUR_ID || targetId == current.getId()) {
			return NavigationResult.none("No neighbour " + dir);
		}

		// Verify the target button exists and is enabled
		if (model.findButtonOnCurrentPage(targetId) == null) {
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
	 * Provides a human-readable description of a button's navigation commands. This is displayed as an overlay when the
	 * user activates a button. Delegates to {@link ParsedNavigationCommand} for proper mnemonic decoding.
	 */
	private String describeNavigationCommands(IgsButton btn) {
		List<NavigationCommand> cmds = btn.getNavigationCommands();
		if (cmds == null || cmds.isEmpty()) {
			return "No navigation commands";
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cmds.size(); i++) {
			NavigationCommand cmd = cmds.get(i);
			sb.append(String.format("[%d] %s: %s", i, cmd.getMnemonic(), cmd.getDescription()));
			if (i < cmds.size() - 1)
				sb.append(" | ");
		}
		return sb.toString();
	}

}
