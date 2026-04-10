package org.brts.middle.scan;

import org.brts.lowlevel.bdmv.NavigationCommandSimulator;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator.SimulationResult;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.IndexBdmv.TitleEntry;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.bdmv.MovieObjects.MovieObject;

import java.util.*;

/**
 * Finds the first playlist that would be played on a non-BD-J Blu-ray disc
 * by chaining HDMV movie-object simulations.
 * <p>
 * Starting from the disc's first-play title (or a configurable override), the
 * finder runs each movie object's navigation commands through
 * {@link NavigationCommandSimulator}. When the simulator terminates with
 * JUMP/CALL, the finder resolves the target and simulates the next object.
 * When it terminates with PLAY_PL*, the playlist number is extracted and returned.
 */
public class FirstPlaylistFinder {

    private static final Set<String> PLAY_REASONS = Set.of(
            "PLAY_PL", "PLAY_PL_PI", "PLAY_PL_PM");

    private static final Set<String> JUMP_REASONS = Set.of(
            "JUMP_OBJECT", "JUMP_TITLE");

    private static final Set<String> CALL_REASONS = Set.of(
            "CALL_OBJECT", "CALL_TITLE");

    private final IndexBdmv index;
    private final MovieObjects movieObjects;

    public FirstPlaylistFinder(IndexBdmv index, MovieObjects movieObjects) {
        this.index = Objects.requireNonNull(index);
        this.movieObjects = Objects.requireNonNull(movieObjects);
    }

    public FirstPlaylistResult find(FirstPlaylistFinderConfig config) {
        List<String> trace = new ArrayList<>();
        long totalSteps = 0;

        // --- Resolve starting object ---
        int currentObjectId;
        if (config.getStartObjectId() != null) {
            currentObjectId = config.getStartObjectId();
            trace.add("start → object " + currentObjectId + " (explicit)");
        } else if (config.getStartTitleNumber() != null) {
            int titleNum = config.getStartTitleNumber();
            currentObjectId = resolveTitleToObject(titleNum, trace);
            if (currentObjectId < 0) {
                return deadEnd(trace, totalSteps, "BD_J_ENCOUNTERED");
            }
        } else {
            TitleEntry fp = index.getFirstPlayTitle();
            if (fp == null) {
                trace.add("no firstPlayTitle defined in index.bdmv");
                return deadEnd(trace, totalSteps, "NO_FIRST_PLAY");
            }
            if (fp.isBdj()) {
                trace.add("firstPlayTitle is BD-J (" + fp.getBdjObjectName() + ")");
                return deadEnd(trace, totalSteps, "BD_J_ENCOUNTERED");
            }
            currentObjectId = fp.getHdmvObjectId();
            trace.add("firstPlay → object " + currentObjectId);
        }

        // --- Build PSR defaults + overrides ---
        Map<Integer, Long> psrMap = buildDefaultPsr();
        if (config.getPsrOverrides() != null) {
            psrMap.putAll(config.getPsrOverrides());
        }

        // --- Call stack for CALL/RESUME ---
        record CallFrame(int objectId, Map<Integer, Long> gprState) {}
        Deque<CallFrame> callStack = new ArrayDeque<>();

        Map<Integer, Long> gprState = null; // no initial GPR state
        int depth = 0;

        while (depth < config.getMaxChainDepth()) {
            if (currentObjectId < 0 || currentObjectId >= movieObjects.getMovieObjects().size()) {
                trace.add("object " + currentObjectId + " out of range (0.."
                        + (movieObjects.getMovieObjects().size() - 1) + ")");
                return deadEnd(trace, totalSteps, "INVALID_OBJECT");
            }

            MovieObject mo = movieObjects.getMovieObjects().get(currentObjectId);
            long stepsRemaining = config.getMaxTotalSteps() - totalSteps;
            if (stepsRemaining <= 0) {
                trace.add("total step budget exhausted");
                return deadEnd(trace, totalSteps, "MAX_STEPS");
            }

            NavigationCommandSimulator sim = new NavigationCommandSimulator(
                    mo.getNavigationCommands(), psrMap, gprState,
                    Math.min(stepsRemaining, config.getMaxTotalSteps()));
            SimulationResult result = sim.run();
            totalSteps += result.stepsExecuted();
            String reason = result.terminationReason();

            // --- PLAY_PL* → success ---
            if (PLAY_REASONS.contains(reason)) {
                long playlistId = result.terminalOp1() != null ? result.terminalOp1() : 0;
                trace.add("object " + currentObjectId + " → " + reason + " " + playlistId);

                FirstPlaylistResult r = new FirstPlaylistResult();
                r.setFound(true);
                r.setPlaylistId((int) playlistId);
                r.setPlayCommand(reason);
                if ("PLAY_PL_PI".equals(reason) && result.terminalOp2() != null) {
                    r.setPlayItemId(result.terminalOp2().intValue());
                } else if ("PLAY_PL_PM".equals(reason) && result.terminalOp2() != null) {
                    r.setPlayMarkId(result.terminalOp2().intValue());
                }
                r.setTrace(trace);
                r.setTerminationReason(reason);
                r.setTotalStepsExecuted(totalSteps);
                return r;
            }

            // --- JUMP_OBJECT ---
            if ("JUMP_OBJECT".equals(reason)) {
                int targetObj = result.terminalOp1() != null ? result.terminalOp1().intValue() : -1;
                trace.add("object " + currentObjectId + " → JUMP_OBJECT " + targetObj);
                currentObjectId = targetObj;
                gprState = result.finalGprState().isEmpty() ? null : result.finalGprState();
                depth++;
                continue;
            }

            // --- JUMP_TITLE ---
            if ("JUMP_TITLE".equals(reason)) {
                int titleNum = result.terminalOp1() != null ? result.terminalOp1().intValue() : -1;
                trace.add("object " + currentObjectId + " → JUMP_TITLE " + titleNum);
                int targetObj = resolveTitleToObject(titleNum, trace);
                if (targetObj < 0) {
                    return deadEnd(trace, totalSteps, "BD_J_ENCOUNTERED");
                }
                psrMap.put(4, (long) titleNum);
                currentObjectId = targetObj;
                gprState = result.finalGprState().isEmpty() ? null : result.finalGprState();
                depth++;
                continue;
            }

            // --- CALL_OBJECT ---
            if ("CALL_OBJECT".equals(reason)) {
                int targetObj = result.terminalOp1() != null ? result.terminalOp1().intValue() : -1;
                trace.add("object " + currentObjectId + " → CALL_OBJECT " + targetObj);
                callStack.push(new CallFrame(currentObjectId, result.finalGprState()));
                currentObjectId = targetObj;
                gprState = result.finalGprState().isEmpty() ? null : result.finalGprState();
                depth++;
                continue;
            }

            // --- CALL_TITLE ---
            if ("CALL_TITLE".equals(reason)) {
                int titleNum = result.terminalOp1() != null ? result.terminalOp1().intValue() : -1;
                trace.add("object " + currentObjectId + " → CALL_TITLE " + titleNum);
                callStack.push(new CallFrame(currentObjectId, result.finalGprState()));
                int targetObj = resolveTitleToObject(titleNum, trace);
                if (targetObj < 0) {
                    return deadEnd(trace, totalSteps, "BD_J_ENCOUNTERED");
                }
                psrMap.put(4, (long) titleNum);
                currentObjectId = targetObj;
                gprState = result.finalGprState().isEmpty() ? null : result.finalGprState();
                depth++;
                continue;
            }

            // --- RESUME ---
            if ("RESUME".equals(reason)) {
                if (callStack.isEmpty()) {
                    trace.add("object " + currentObjectId + " → RESUME with empty call stack");
                    return deadEnd(trace, totalSteps, "RESUME_NO_CALLER");
                }
                CallFrame frame = callStack.pop();
                trace.add("object " + currentObjectId + " → RESUME → object " + frame.objectId());
                currentObjectId = frame.objectId();
                gprState = frame.gprState().isEmpty() ? null : frame.gprState();
                depth++;
                continue;
            }

            // --- BREAK / END / MAX_STEPS / other ---
            trace.add("object " + currentObjectId + " → " + reason);
            return deadEnd(trace, totalSteps, "DEAD_END");
        }

        trace.add("max chain depth (" + config.getMaxChainDepth() + ") exceeded");
        return deadEnd(trace, totalSteps, "MAX_DEPTH");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a JUMP_TITLE/CALL_TITLE operand to a movie object index.
     * <p>
     * Title number conventions:
     * <ul>
     *   <li>0 → top menu title</li>
     *   <li>0xFFFF → first play title</li>
     *   <li>1..N → titles[n-1]</li>
     * </ul>
     *
     * @return movie object index, or -1 if the title is BD-J or invalid
     */
    private int resolveTitleToObject(int titleNumber, List<String> trace) {
        TitleEntry entry;
        if (titleNumber == 0) {
            entry = index.getTopMenuTitle();
            if (entry == null) {
                trace.add("title 0 (top menu) not defined");
                return -1;
            }
        } else if (titleNumber == 0xFFFF) {
            entry = index.getFirstPlayTitle();
            if (entry == null) {
                trace.add("title 0xFFFF (first play) not defined");
                return -1;
            }
        } else {
            int idx = titleNumber - 1;
            if (index.getTitles() == null || idx < 0 || idx >= index.getTitles().size()) {
                trace.add("title " + titleNumber + " out of range");
                return -1;
            }
            entry = index.getTitles().get(idx);
        }

        if (entry.isBdj()) {
            trace.add("title " + titleNumber + " is BD-J (" + entry.getBdjObjectName() + ")");
            return -1;
        }
        return entry.getHdmvObjectId();
    }

    private FirstPlaylistResult deadEnd(List<String> trace, long totalSteps, String reason) {
        FirstPlaylistResult r = new FirstPlaylistResult();
        r.setFound(false);
        r.setTrace(trace);
        r.setTerminationReason(reason);
        r.setTotalStepsExecuted(totalSteps);
        return r;
    }

    /**
     * Builds the default PSR map with sensible Blu-ray player values.
     */
    static Map<Integer, Long> buildDefaultPsr() {
        Map<Integer, Long> psr = new HashMap<>();
        psr.put(0, 1L);        // IG stream number = 1
        psr.put(1, 1L);        // primary audio stream = 1
        psr.put(2, 0L);        // PG/subtitle stream = off
        psr.put(3, 1L);        // angle number = 1
        psr.put(4, 0xFFFFL);   // title number = first play
        psr.put(5, 1L);        // chapter number = 1
        psr.put(6, 0L);        // playlist ID (not yet playing)
        psr.put(7, 0L);        // play item ID
        psr.put(8, 0L);        // presentation time
        psr.put(9, 0L);        // navigation timer
        psr.put(10, 0xFFFFL);  // selected button ID = auto-select
        psr.put(11, 0L);       // page ID
        psr.put(12, 0xFFL);    // parental level = unrestricted
        psr.put(13, 0xFFL);    // secondary audio capability = all
        psr.put(14, 0L);       // audio mix mode = default
        psr.put(15, 0L);       // country code = not set
        psr.put(16, 1L);       // region code = Region A
        psr.put(20, 0x656EL);  // language code = "en"
        psr.put(29, 0x0200L);  // player profile version = 2.0
        psr.put(31, 4L);       // player capability (UHD)
        return psr;
    }
}
