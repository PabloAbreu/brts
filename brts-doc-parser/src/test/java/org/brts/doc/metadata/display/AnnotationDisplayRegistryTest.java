package org.brts.doc.metadata.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;
import org.junit.jupiter.api.Test;

class AnnotationDisplayRegistryTest {

	private final AnnotationDisplayRegistry registry = AnnotationDisplayRegistry.defaults();

	@Test
	void suppressesArgs4jAndJsonInputOptions() {
		List<AnnotationMetadata> annotations = List.of(
				new AnnotationMetadata("org.kohsuke.args4j.Option", Map.of("name", "--descriptor")),
				new AnnotationMetadata("org.brts.cli.JsonInputOption", Map.of("name", "--descriptor")));

		assertThat(registry.display(annotations)).isEmpty();
	}

	@Test
	void dropsDefaultMessageKeyAndConstraintPlumbing() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("groups", List.of());
		attributes.put("message", "{jakarta.validation.constraints.NotBlank.message}");
		attributes.put("payload", List.of());
		AnnotationMetadata annotation = new AnnotationMetadata("jakarta.validation.constraints.NotBlank", attributes);

		assertThat(registry.display(List.of(annotation)))
				.containsExactly(new DisplayedAnnotation("NotBlank", List.of()));
	}

	@Test
	void keepsMeaningfulConstraintAttributes() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("flags", List.of());
		attributes.put("groups", List.of());
		attributes.put("message", "discName must not contain special characters");
		attributes.put("payload", List.of());
		attributes.put("regexp", "[A-Za-z0-9 _]+");
		AnnotationMetadata annotation = new AnnotationMetadata("jakarta.validation.constraints.Pattern", attributes);

		assertThat(registry.display(List.of(annotation))).containsExactly(new DisplayedAnnotation("Pattern",
				List.of(new DisplayedAttribute("message", "discName must not contain special characters"),
						new DisplayedAttribute("regexp", "[A-Za-z0-9 _]+"))));
	}

	@Test
	void rendersValidAsMarker() {
		AnnotationMetadata annotation = new AnnotationMetadata("jakarta.validation.Valid", Map.of());

		assertThat(registry.display(List.of(annotation))).containsExactly(new DisplayedAnnotation("Valid", List.of()));
	}

	@Test
	void fallsBackToSimpleNameAndNonEmptyAttributes() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("blank", "  ");
		attributes.put("empty", List.of());
		attributes.put("missing", null);
		attributes.put("values", List.of("a", "b"));
		AnnotationMetadata annotation = new AnnotationMetadata("com.example.Outer$Custom", attributes);

		assertThat(registry.display(List.of(annotation)))
				.containsExactly(new DisplayedAnnotation("Custom", List.of(new DisplayedAttribute("values", "a, b"))));
	}

	@Test
	void customHandlerTakesPrecedenceOverDefaults() {
		AnnotationDisplayHandler handler = new AnnotationDisplayHandler() {

			@Override
			public boolean supports(AnnotationMetadata annotation) {
				return "jakarta.validation.Valid".equals(annotation.type());
			}

			@Override
			public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
				return Optional.of(new DisplayedAnnotation("NestedValidation"));
			}
		};
		AnnotationDisplayRegistry custom = new AnnotationDisplayRegistry(List.of(handler));

		assertThat(custom.display(List.of(new AnnotationMetadata("jakarta.validation.Valid", Map.of()))))
				.containsExactly(new DisplayedAnnotation("NestedValidation", List.of()));
	}
}
