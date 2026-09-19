package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/AnnotationDisplayHandler.java' is part of BRTS.
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

import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Turns raw annotation metadata into a presentation-neutral simplified form.
 */
public interface AnnotationDisplayHandler {

	boolean supports(AnnotationMetadata annotation);

	/** An empty result suppresses the annotation from the documentation. */
	Optional<DisplayedAnnotation> display(AnnotationMetadata annotation);
}
