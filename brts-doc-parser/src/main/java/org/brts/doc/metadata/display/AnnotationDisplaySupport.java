package org.brts.doc.metadata.display;

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
