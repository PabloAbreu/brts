package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/AnnotationDisplayRegistry.java' is part of BRTS.
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

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Resolves annotations through the first handler claiming them, falling back to a generic rendering.
 */
public final class AnnotationDisplayRegistry {

	private final List<AnnotationDisplayHandler> handlers;

	private final AnnotationDisplayHandler fallback = new DefaultAnnotationDisplayHandler();

	public AnnotationDisplayRegistry(List<AnnotationDisplayHandler> handlers) {
		if (handlers == null) {
			throw new IllegalArgumentException("Annotation display handlers must not be null");
		}
		this.handlers = List.copyOf(handlers);
	}

	public static AnnotationDisplayRegistry defaults() {
		return new AnnotationDisplayRegistry(List.of(new SuppressedAnnotationDisplayHandler(),
				new ValidationConstraintDisplayHandler(), new ValidAnnotationDisplayHandler()));
	}

	public List<DisplayedAnnotation> display(List<AnnotationMetadata> annotations) {
		List<DisplayedAnnotation> displayed = new ArrayList<>();
		for (AnnotationMetadata annotation : annotations) {
			handler(annotation).display(annotation).ifPresent(displayed::add);
		}
		return List.copyOf(displayed);
	}

	private AnnotationDisplayHandler handler(AnnotationMetadata annotation) {
		return handlers.stream().filter(handler -> handler.supports(annotation)).findFirst().orElse(fallback);
	}
}
