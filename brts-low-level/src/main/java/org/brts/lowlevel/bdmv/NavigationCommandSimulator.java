package org.brts.lowlevel.bdmv;

import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

import java.util.*;
import java.util.function.LongPredicate;

/**
 * Simulates execution of a list of HDMV {@link NavigationCommand}s.
 * <p>
 * Internal engine commands (GOTO, SET, COMPARE, etc.) are fully executed against a {@link GprState} register file.
 * Commands that would have an external effect (PLAY, JUMP, SET_SYSTEM, etc.) are traced but not executed.
 * <p>
 * Operand types (immediate vs register) are determined from the opcode's immediate flags (bits 23 and 22) via
 * {@link ParsedNavigationCommand}.
 * <p>
 * A single instance may be reused across multiple {@link #run(List)} calls (e.g. to simulate successive button
 * activations while sharing one {@link GprState}); it is not thread-safe / not reentrant.
 */
public class NavigationCommandSimulator {

	private static final long MASK_32 = 0xFFFF_FFFFL;

	private static final long PSR_BIT = 0x8000_0000L;

	private final GprState gprState;

	private final Long[] psr = new Long[128];

	private final long maxSteps;

	private int pc = 0;

	private final Random random = new Random();

	private final List<String> externalEffects = new ArrayList<>();

	/**
	 * Optional predicate applied to PLAY_PL/PLAY_PL_PI/PLAY_PL_PM playlist IDs. When non-null, the simulator terminates
	 * only when the predicate returns {@code true}; otherwise it continues to the next instruction. {@code null} means
	 * always terminate (default behaviour).
	 */
	private final LongPredicate playlistTerminator;

	/**
	 * @param psrInit  initial PSR values (may be {@code null}); reading an uninitialised PSR at runtime will throw
	 *                 {@link SimulationException}
	 * @param maxSteps maximum number of instructions to execute before aborting
	 */
	public NavigationCommandSimulator(Map<Integer, Long> psrInit, long maxSteps) {
		this(psrInit, new GprState(), maxSteps, null);
	}

	/**
	 * @param psrInit  initial PSR values (may be {@code null})
	 * @param gprInit  initial GPR values (may be {@code null}); used to carry state across chained simulations (e.g.
	 *                 CALL/RESUME flows)
	 * @param maxSteps maximum number of instructions to execute before aborting
	 */
	public NavigationCommandSimulator(Map<Integer, Long> psrInit, Map<Integer, Long> gprInit, long maxSteps) {
		this(psrInit, new GprState(gprInit), maxSteps, null);
	}

	/**
	 * @param psrInit            initial PSR values (may be {@code null})
	 * @param gprInit            initial GPR values (may be {@code null})
	 * @param maxSteps           maximum number of instructions to execute before aborting
	 * @param playlistTerminator optional predicate on the playlist ID for PLAY_PL/PLAY_PL_PI/PLAY_PL_PM; when non-null
	 *                           the simulator terminates only when the predicate returns {@code true}, otherwise
	 *                           execution continues past the PLAY command
	 */
	public NavigationCommandSimulator(Map<Integer, Long> psrInit, Map<Integer, Long> gprInit, long maxSteps,
			LongPredicate playlistTerminator) {
		this(psrInit, new GprState(gprInit), maxSteps, playlistTerminator);
	}

	/**
	 * @param psrInit  initial PSR values (may be {@code null})
	 * @param gprState GPR register bank to read/write; shared with the caller so state (and the "explicitly set" bit)
	 *                 persists across successive {@link #run(List)} calls on the same instance
	 * @param maxSteps maximum number of instructions to execute before aborting
	 */
	public NavigationCommandSimulator(Map<Integer, Long> psrInit, GprState gprState, long maxSteps) {
		this(psrInit, gprState, maxSteps, null);
	}

	/**
	 * @param psrInit            initial PSR values (may be {@code null})
	 * @param gprState           GPR register bank to read/write (see
	 *                           {@link #NavigationCommandSimulator(Map, GprState, long)})
	 * @param maxSteps           maximum number of instructions to execute before aborting
	 * @param playlistTerminator optional predicate on the playlist ID for PLAY_PL/PLAY_PL_PI/PLAY_PL_PM; when non-null
	 *                           the simulator terminates only when the predicate returns {@code true}, otherwise
	 *                           execution continues past the PLAY command
	 */
	public NavigationCommandSimulator(Map<Integer, Long> psrInit, GprState gprState, long maxSteps,
			LongPredicate playlistTerminator) {
		this.gprState = Objects.requireNonNull(gprState);
		this.maxSteps = maxSteps;
		this.playlistTerminator = playlistTerminator;
		if (psrInit != null) {
			psrInit.forEach((idx, val) -> {
				if (idx < 0 || idx > 127)
					throw new IllegalArgumentException("Invalid PSR index: " + idx);
				psr[idx] = val & MASK_32;
			});
		}
	}

	// -------------------------------------------------------------------------
	// Result
	// -------------------------------------------------------------------------

	public record SimulationResult(List<String> externalEffects, Map<Integer, Long> finalGprState, int finalPc,
			long stepsExecuted, String terminationReason, Long terminalOp1, Long terminalOp2) {
	}

	// -------------------------------------------------------------------------
	// Main execution loop
	// -------------------------------------------------------------------------

	public SimulationResult run(List<NavigationCommand> commands) {
		Objects.requireNonNull(commands);
		pc = 0;
		externalEffects.clear();
		long steps = 0;
		String terminationReason = "END";
		Long terminalOp1 = null;
		Long terminalOp2 = null;

		while (steps < maxSteps) {
			if (pc < 0 || pc >= commands.size()) {
				break;
			}

			NavigationCommand cmd = commands.get(pc);
			NavigationCommandMnemonic m = resolveMnemonic(cmd);
			boolean incPc = true;

			switch (m) {
			// ── BRANCH / GOTO ──────────────────────────────────────────
			case NOP -> {
				/* nothing */ }
			case GOTO -> {
				pc = (int) resolveOperand(cmd, 1);
				incPc = false;
			}
			case BREAK -> {
				terminationReason = "BREAK";
				pc = commands.size();
				incPc = false;
			}

			// ── BRANCH / JUMP — external, terminate ────────────────────
			case JUMP_OBJECT, JUMP_TITLE, CALL_OBJECT, CALL_TITLE, RESUME -> {
				traceExternal(m, cmd, steps);
				terminationReason = m.getMnemonic();
				terminalOp1 = safeResolveOperand(cmd, 1, m);
				terminalOp2 = safeResolveOperand(cmd, 2, m);
				pc = commands.size();
				incPc = false;
			}

			// ── BRANCH / PLAY_PL* — external; may be filtered by playlistTerminator ─
			case PLAY_PL, PLAY_PL_PI, PLAY_PL_PM -> {
				long plId = safeResolveOperand(cmd, 1, m) != null ? safeResolveOperand(cmd, 1, m) : 0L;
				traceExternal(m, cmd, steps);
				if (playlistTerminator == null || playlistTerminator.test(plId)) {
					terminationReason = m.getMnemonic();
					terminalOp1 = plId;
					terminalOp2 = safeResolveOperand(cmd, 2, m);
					pc = commands.size();
					incPc = false;
				}
			}

			// ── BRANCH / PLAY — external, always terminate ─────────────
			case TERMINATE_PL, LINK_PI, LINK_MK -> {
				traceExternal(m, cmd, steps);
				terminationReason = m.getMnemonic();
				terminalOp1 = safeResolveOperand(cmd, 1, m);
				terminalOp2 = safeResolveOperand(cmd, 2, m);
				pc = commands.size();
				incPc = false;
			}

			// ── COMPARE — skip next instruction if condition false ─────
			case BC -> conditionalSkip(cmd, (d, s) -> (d & ~s & MASK_32) == 0);
			case EQ -> conditionalSkip(cmd, (d, s) -> d == s);
			case NE -> conditionalSkip(cmd, (d, s) -> d != s);
			case GE -> conditionalSkip(cmd, (d, s) -> Long.compareUnsigned(d, s) >= 0);
			case GT -> conditionalSkip(cmd, (d, s) -> Long.compareUnsigned(d, s) > 0);
			case LE -> conditionalSkip(cmd, (d, s) -> Long.compareUnsigned(d, s) <= 0);
			case LT -> conditionalSkip(cmd, (d, s) -> Long.compareUnsigned(d, s) < 0);

			// ── SET — arithmetic / logic ───────────────────────────────
			case MOVE -> executeSet(cmd, (d, s) -> s);
			case SWAP -> executeSwap(cmd);
			case ADD -> executeSet(cmd, (d, s) -> Math.min(d + s, MASK_32));
			case SUB -> executeSet(cmd, (d, s) -> d > s ? d - s : 0);
			case MUL -> executeSet(cmd, (d, s) -> (d * s) & MASK_32);
			case DIV -> executeSet(cmd, (d, s) -> s > 0 ? d / s : MASK_32);
			case MOD -> executeSet(cmd, (d, s) -> s > 0 ? d % s : MASK_32);
			case RND -> executeSet(cmd, (d, s) -> s > 0 ? random.nextLong(s) + 1 : 0);
			case AND -> executeSet(cmd, (d, s) -> d & s);
			case OR -> executeSet(cmd, (d, s) -> d | s);
			case XOR -> executeSet(cmd, (d, s) -> d ^ s);
			case BITSET -> executeSet(cmd, (d, s) -> d | (1L << (s & 31)));
			case BITCLR -> executeSet(cmd, (d, s) -> d & ~(1L << (s & 31)));
			case SHL -> executeSet(cmd, (d, s) -> (d << (s & 31)) & MASK_32);
			case SHR -> executeSet(cmd, (d, s) -> (d & MASK_32) >>> (s & 31));

			// ── SET_SYSTEM — external, continue ────────────────────────
			case SET_STREAM, SET_NV_TIMER, SET_BUTTON_PAGE, ENABLE_BUTTON, DISABLE_BUTTON, SET_SEC_STREAM, POPUP_OFF,
					STILL_ON, STILL_OFF, SET_OUTPUT_MODE, SET_STREAM_SS -> {
				traceExternal(m, cmd, steps);
			}
			}

			if (incPc)
				pc++;
			steps++;
		}

		if (steps >= maxSteps) {
			terminationReason = "MAX_STEPS";
		}

		return new SimulationResult(Collections.unmodifiableList(new ArrayList<>(externalEffects)), gprState.asMap(),
				pc, steps, terminationReason, terminalOp1, terminalOp2);
	}

	// -------------------------------------------------------------------------
	// Operand resolution
	// -------------------------------------------------------------------------

	private long resolveOperand(NavigationCommand cmd, int operandIndex) {
		ParsedNavigationCommand parsed = cmd.toParsed();
		long raw = (operandIndex == 1) ? parsed.getOperand1() : parsed.getOperand2();
		boolean immediate = (operandIndex == 1) ? parsed.isOp1Immediate() : parsed.isOp2Immediate();
		if (immediate)
			return raw & MASK_32;
		return readRegister(raw);
	}

	private long readRegister(long operandValue) {
		if ((operandValue & PSR_BIT) != 0) {
			int index = (int) (operandValue & 0x7F);
			if (psr[index] == null) {
				throw new SimulationException("PSR" + index + " was read but not provided in input (pc=" + pc + ")");
			}
			return psr[index];
		}
		int index = (int) (operandValue & 0xFFF);
		return gprState.get(index);
	}

	private void storeResult(NavigationCommand cmd, int operandIndex, long value) {
		ParsedNavigationCommand parsed = cmd.toParsed();
		long raw = (operandIndex == 1) ? parsed.getOperand1() : parsed.getOperand2();
		boolean immediate = (operandIndex == 1) ? parsed.isOp1Immediate() : parsed.isOp2Immediate();
		if (immediate) {
			throw new SimulationException("Cannot store result to immediate value (pc=" + pc + ")");
		}
		if ((raw & PSR_BIT) != 0) {
			throw new SimulationException("Cannot write to PSR from SET instruction (pc=" + pc + ")");
		}
		int index = (int) (raw & 0xFFF);
		gprState.set(index, value & MASK_32);
	}

	// -------------------------------------------------------------------------
	// Instruction helpers
	// -------------------------------------------------------------------------

	private NavigationCommandMnemonic resolveMnemonic(NavigationCommand cmd) {
		ParsedNavigationCommand parsed = cmd.toParsed();
		NavigationCommandMnemonic m = parsed.getMnemonic();
		if (m != null)
			return m;
		throw new SimulationException("Unknown command: " + parsed.getMnemonicString() + " (pc=" + pc + ")");
	}

	@FunctionalInterface
	private interface BinaryOp {

		long apply(long dst, long src);

	}

	@FunctionalInterface
	private interface CompareOp {

		boolean test(long dst, long src);

	}

	private void conditionalSkip(NavigationCommand cmd, CompareOp op) {
		long dst = resolveOperand(cmd, 1);
		long src = resolveOperand(cmd, 2);
		if (!op.test(dst, src))
			pc++;
	}

	private void executeSet(NavigationCommand cmd, BinaryOp op) {
		long dst = resolveOperand(cmd, 1);
		long src = resolveOperand(cmd, 2);
		long result = op.apply(dst, src) & MASK_32;
		storeResult(cmd, 1, result);
	}

	private void executeSwap(NavigationCommand cmd) {
		long dstVal = resolveOperand(cmd, 1);
		long srcVal = resolveOperand(cmd, 2);
		storeResult(cmd, 1, srcVal);
		storeResult(cmd, 2, dstVal);
	}

	// -------------------------------------------------------------------------
	// External-effect tracing
	// -------------------------------------------------------------------------

	private void traceExternal(NavigationCommandMnemonic m, NavigationCommand cmd, long step) {
		StringBuilder sb = new StringBuilder();
		sb.append("[step ").append(step).append(", pc=").append(pc).append("] ");
		sb.append(m.getMnemonic());
		if (m == NavigationCommandMnemonic.SET_BUTTON_PAGE) {
			// operand encoding reuses the PSR discriminator bit as a button/page-enabled flag; decode separately
			sb.append(' ').append(formatSetButtonPage(cmd));
		} else {
			if (m.getOperandCount() >= 1) {
				sb.append(" op1=").append(formatOperandValue(cmd, 1));
			}
			if (m.getOperandCount() >= 2) {
				sb.append(" op2=").append(formatOperandValue(cmd, 2));
			}
		}
		externalEffects.add(sb.toString());
	}

	private String formatSetButtonPage(NavigationCommand cmd) {
		try {
			ParsedNavigationCommand.ButtonPageTarget target = cmd.toParsed().resolveButtonPageTarget(gprSnapshot());
			return "button=" + (target.buttonId().isPresent() ? target.buttonId().getAsInt() : "none") + " page="
					+ (target.pageId().isPresent() ? target.pageId().getAsInt() : "none") + " effect="
					+ (target.effectOff() ? "off" : "on");
		} catch (IllegalStateException e) {
			return "?(" + e.getMessage() + ")";
		}
	}

	private Map<Integer, Long> gprSnapshot() {
		return gprState.asMap();
	}

	private String formatOperandValue(NavigationCommand cmd, int operandIndex) {
		try {
			ParsedNavigationCommand parsed = cmd.toParsed();
			long raw = (operandIndex == 1) ? parsed.getOperand1() : parsed.getOperand2();
			long resolved = resolveOperand(cmd, operandIndex);
			boolean immediate = (operandIndex == 1) ? parsed.isOp1Immediate() : parsed.isOp2Immediate();
			if (immediate)
				return String.valueOf(resolved);
			if ((raw & PSR_BIT) != 0)
				return "PSR" + (raw & 0x7F) + "=" + resolved;
			return "GPR[" + (raw & 0xFFF) + "]=" + resolved;
		} catch (SimulationException | IllegalStateException e) {
			return "?(" + e.getMessage() + ")";
		}
	}

	private Long safeResolveOperand(NavigationCommand cmd, int operandIndex, NavigationCommandMnemonic m) {
		if (m.getOperandCount() < operandIndex)
			return null;
		try {
			return resolveOperand(cmd, operandIndex);
		} catch (SimulationException e) {
			return null;
		}
	}

	// -------------------------------------------------------------------------
	// Exception
	// -------------------------------------------------------------------------

	public static class SimulationException extends RuntimeException {

		public SimulationException(String message) {
			super(message);
		}

	}

}
