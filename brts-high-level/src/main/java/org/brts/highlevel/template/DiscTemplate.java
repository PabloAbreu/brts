package org.brts.highlevel.template;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-high-level/src/main/java/org/brts/highlevel/template/DiscTemplate.java' is part of BRTS.
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

import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.middle.descriptor.DiscDescriptor;

/**
 * Contract for all high-level disc authoring templates.
 * <p>
 * A template translates a {@link HighLevelDiscDescriptor} into a {@link DiscDescriptor} (middle-level), plus any
 * additional metadata (e.g. menu structure) the middle-level layer needs.
 */
public interface DiscTemplate<D extends HighLevelDiscDescriptor> {

	/**
	 * Expands the high-level descriptor into a middle-level disc descriptor.
	 *
	 * @param descriptor the high-level input
	 * @return the equivalent middle-level representation
	 */
	DiscDescriptor expand(D descriptor) throws java.io.IOException;

	/** Returns the template type token this implementation handles (e.g. "MOVIE"). */
	String templateType();

}
