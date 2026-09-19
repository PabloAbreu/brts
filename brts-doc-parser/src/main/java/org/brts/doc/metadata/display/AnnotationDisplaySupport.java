package org.brts.doc.metadata.display;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/metadata/display/AnnotationDisplaySupport.java' is part of BRTS.
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class AnnotationDisplaySupport {

	private AnnotationDisplaySupport() {
	}

	static String simpleName(String qualifiedName) {
		if (qualifiedName == null || qualifiedName.isBlank()) {
			return qualifiedName;
		}
		int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}

	static boolean isNoise(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String text) {
			return text.isBlank();
		}
		if (value instanceof Collection<?> values) {
			return values.isEmpty();
		}
		if (value instanceof Map<?, ?> values) {
			return values.isEmpty();
		}
		return false;
	}

	static String format(Object value) {
		if (value instanceof Collection<?> values) {
			List<String> formatted = new ArrayList<>();
			values.forEach(element -> formatted.add(format(element)));
			return String.join(", ", formatted);
		}
		if (value instanceof Map<?, ?> values) {
			List<String> formatted = new ArrayList<>();
			values.forEach((key, element) -> formatted.add(key + "=" + format(element)));
			return String.join(", ", formatted);
		}
		return String.valueOf(value);
	}

	static List<DisplayedAttribute> attributes(Map<String, Object> attributes, Collection<String> excluded) {
		List<DisplayedAttribute> displayed = new ArrayList<>();
		attributes.forEach((label, value) -> {
			if (!excluded.contains(label) && !isNoise(value)) {
				displayed.add(new DisplayedAttribute(label, format(value)));
			}
		});
		return List.copyOf(displayed);
	}
}
