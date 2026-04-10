package org.brts.middle.preview;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Result of a navigation action (move / activate / page-change).
 */
@Getter
@RequiredArgsConstructor
public class NavigationResult {

    public enum Type {
        /** Nothing happened (e.g. no neighbour). */
        NONE,
        /** Selection moved to a different button. */
        SELECTION_CHANGED,
        /** A button was activated (Enter pressed). */
        ACTIVATED,
        /** Page changed. */
        PAGE_CHANGED
    }

    private final Type type;
    private final int buttonOrPageId;
    private final String description;

    public static NavigationResult none(String reason) {
        return new NavigationResult(Type.NONE, -1, reason);
    }
}
