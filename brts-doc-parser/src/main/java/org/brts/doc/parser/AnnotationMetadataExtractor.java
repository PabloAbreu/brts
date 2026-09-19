package org.brts.doc.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/parser/AnnotationMetadataExtractor.java' is part of BRTS.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.doc.metadata.AnnotationMetadata;

final class AnnotationMetadataExtractor {

	private static final List<String> EXPORTED_PACKAGES = List.of("org.kohsuke.args4j", "org.brts.cli",
			"jakarta.validation", "org.brts.common.validation");

	List<AnnotationMetadata> extract(Annotation[] annotations) {
		return Arrays.stream(annotations).filter(this::isExported)
				.sorted(Comparator.comparing(a -> a.annotationType().getName())).map(this::extract).toList();
	}

	private boolean isExported(Annotation annotation) {
		String type = annotation.annotationType().getName();
		return EXPORTED_PACKAGES.stream().anyMatch(type::startsWith);
	}

	private AnnotationMetadata extract(Annotation annotation) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		Arrays.stream(annotation.annotationType().getDeclaredMethods()).sorted(Comparator.comparing(Method::getName))
				.forEach(method -> attributes.put(method.getName(), readAttribute(annotation, method)));
		return new AnnotationMetadata(annotation.annotationType().getName(), attributes);
	}

	private Object readAttribute(Annotation annotation, Method method) {
		try {
			return normalize(method.invoke(annotation));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot read annotation attribute " + method, e);
		}
	}

	private Object normalize(Object value) {
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		if (value instanceof Class<?> type) {
			return type.getName();
		}
		if (value instanceof Enum<?> enumValue) {
			return enumValue.name();
		}
		if (value instanceof Annotation annotation) {
			return extract(annotation).attributes();
		}
		if (value.getClass().isArray()) {
			List<Object> values = new ArrayList<>();
			for (int index = 0; index < Array.getLength(value); index++) {
				values.add(normalize(Array.get(value, index)));
			}
			return values;
		}
		return value.toString();
	}
}
