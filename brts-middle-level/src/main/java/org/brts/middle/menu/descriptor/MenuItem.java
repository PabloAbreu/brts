package org.brts.middle.menu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Common base for all setup-menu item types.
 * <p>
 * Consolidates the fields shared by every item: a stable cross-reference id,
 * a human-readable label, an optional per-item style override, and optional
 * explicit D-pad navigation overrides.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class MenuItem {

    /**
     * Optional stable identifier for cross-item navigation references.
     * When set, other items can reference this button by id in their
     * {@link NavigationRefs}.
     */
    private String id;

    /**
     * Human-readable label for the button.
     */
    private String description;

    /**
     * Optional per-item style override.
     * Non-null fields are merged over the global style.
     */
    private TextStyle style;

    /**
     * Optional explicit directional navigation overrides.
     * Directions not specified here are filled by auto-wiring.
     */
    private NavigationRefs nav;
}
