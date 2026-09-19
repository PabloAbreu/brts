package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsButton.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An IGS button — a clickable menu element with three visual states (normal / selected / activated), four navigation
 * neighbours, and optional navigation commands.
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
	private List<NavigationCommand> navigationCommands = new ArrayList<>();

}
