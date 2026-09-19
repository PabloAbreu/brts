package org.brts.doc.metadata.display;

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
