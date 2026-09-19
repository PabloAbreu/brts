package org.brts.doc.metadata.display;

import java.util.List;

public record DisplayedAnnotation(String name, List<DisplayedAttribute> attributes) {

	public DisplayedAnnotation(String name) {
		this(name, List.of());
	}
}
