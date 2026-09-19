package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/ValidationConstraintDisplayHandler.java' is part of BRTS.
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Renders bean validation constraints, keeping only the attributes that carry constraint semantics.
 */
public final class ValidationConstraintDisplayHandler implements AnnotationDisplayHandler {

	private static final List<String> SUPPORTED_PACKAGES = List.of("jakarta.validation.constraints.",
			"org.brts.common.validation.");

	private static final Set<String> EXCLUDED = Set.of("groups", "payload", "flags");

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return SUPPORTED_PACKAGES.stream().anyMatch(annotation.type()::startsWith);
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		Map<String, Object> attributes = new LinkedHashMap<>(annotation.attributes());
		if (isDefaultMessage(attributes.get("message"))) {
			attributes.remove("message");
		}
		return Optional.of(new DisplayedAnnotation(AnnotationDisplaySupport.simpleName(annotation.type()),
				AnnotationDisplaySupport.attributes(attributes, EXCLUDED)));
	}

	/** Unresolved bundle keys such as {jakarta.validation.constraints.NotBlank.message}. */
	private boolean isDefaultMessage(Object message) {
		return message instanceof String text && text.startsWith("{") && text.endsWith("}");
	}
}
