package org.brts.highlevel.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-high-level/src/main/java/org/brts/highlevel/descriptor/HighLevelDiscDescriptor.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Root high-level disc descriptor.
 * <p>
 * The {@code templateType} field selects the authoring template:
 * <ul>
 * <li>{@code "MOVIE"} → {@link MovieDiscDescriptor}</li>
 * <li>{@code "TV_SERIES"} → {@link TvSeriesDiscDescriptor}</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "templateType")
@JsonSubTypes({ @JsonSubTypes.Type(value = MovieDiscDescriptor.class, name = "MOVIE"),
		@JsonSubTypes.Type(value = TvSeriesDiscDescriptor.class, name = "TV_SERIES") })
@Getter
@Setter
public abstract class HighLevelDiscDescriptor {

	private String templateType;

	private String discTitle;

	private String outputDirectory;

	/**
	 * If true, the high-level step only generates middle-level descriptors without running them.
	 */
	private boolean dryRun = false;

}
