package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * An IGS button — a clickable menu element with three visual states
 * (normal / selected / activated), four navigation neighbours, and optional
 * navigation commands.
 */
@Getter
@Setter
@ToString(exclude = "navigationCommands")
public class IgsButton {
    /** Button id (16 bits). */
    private int id;
    /** Numeric select value (16 bits). */
    private int numericSelectValue;
    /** Auto-action flag (1 bit). */
    private boolean autoAction;
    /** Horizontal position (16 bits). */
    private int xPos;
    /** Vertical position (16 bits). */
    private int yPos;

    // Neighbour references (16 bits each)
    private int upperButtonIdRef;
    private int lowerButtonIdRef;
    private int leftButtonIdRef;
    private int rightButtonIdRef;

    // Normal state
    private int normalStartObjectIdRef;
    private int normalEndObjectIdRef;
    private boolean normalRepeatFlag;

    // Selected state
    private int selectedSoundIdRef;
    private int selectedStartObjectIdRef;
    private int selectedEndObjectIdRef;
    private boolean selectedRepeatFlag;

    // Activated state
    private int activatedSoundIdRef;
    private int activatedStartObjectIdRef;
    private int activatedEndObjectIdRef;

    /** Navigation commands for this button. */
    private List<ParsedNavigationCommand> navigationCommands = new ArrayList<>();
}
