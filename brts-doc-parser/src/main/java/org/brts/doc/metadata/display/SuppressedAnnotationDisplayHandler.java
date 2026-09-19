package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/SuppressedAnnotationDisplayHandler.java' is part of BRTS.
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

import java.util.List;
import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Hides annotations whose content is already rendered as first-class metadata.
 */
public final class SuppressedAnnotationDisplayHandler implements AnnotationDisplayHandler {

	private static final List<String> SUPPRESSED = List.of("org.kohsuke.args4j.Option", "org.kohsuke.args4j.Argument",
			"org.brts.cli.JsonInputOption");

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return SUPPRESSED.contains(annotation.type());
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		return Optional.empty();
	}
}
