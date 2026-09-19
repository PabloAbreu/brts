package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/ValidAnnotationDisplayHandler.java' is part of BRTS.
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
 * {@code @Valid} is a marker: its attributes never carry information.
 */
public final class ValidAnnotationDisplayHandler implements AnnotationDisplayHandler {

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return "jakarta.validation.Valid".equals(annotation.type());
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		return Optional.of(new DisplayedAnnotation("Valid"));
	}
}
