package org.brts.doc.parser;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.cli.BrtsMain;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.cli.LevelDispatcher;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.FieldMetadata;
import org.brts.doc.metadata.LevelMetadata;
import org.brts.doc.metadata.OptionMetadata;
import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.TypeMetadata;
import org.kohsuke.args4j.Option;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public final class CliMetadataParser {

	public static final String SCHEMA_VERSION = "1.0";

	private final SourceDocumentationIndex documentation;
	private final AnnotationMetadataExtractor annotations = new AnnotationMetadataExtractor();
	private final Map<String, TypeMetadata> types = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();
	private final Deque<Class<?>> pendingTypes = new ArrayDeque<>();

	public CliMetadataParser(List<Path> sourceRoots) throws IOException {
		this.documentation = SourceDocumentationIndex.create(sourceRoots);
	}

	public CliMetadata parse(String applicationVersion) {
		List<LevelMetadata> levels = new ArrayList<>();
		for (Map.Entry<String, LevelDispatcher> entry : BrtsMain.getLevels().entrySet()) {
			List<CommandMetadata> commands = entry.getValue().getRunners().stream()
					.map(runner -> command(entry.getKey(), runner)).toList();
			levels.add(new LevelMetadata(entry.getKey(), commands));
		}
		while (!pendingTypes.isEmpty()) {
			inspectType(pendingTypes.removeFirst());
		}
		return new CliMetadata(SCHEMA_VERSION, applicationVersion, levels, Collections.unmodifiableMap(types),
				List.copyOf(warnings));
	}

	private CommandMetadata command(String level, FeatureRunner<?> runner) {
		Class<?> optionsType = runner.getOptionsType();
		List<OptionMetadata> options = new ArrayList<>();
		for (Class<?> type : hierarchy(optionsType)) {
			for (Field field : type.getDeclaredFields()) {
				Option option = field.getAnnotation(Option.class);
				JsonInputOption jsonOption = field.getAnnotation(JsonInputOption.class);
				if (option != null || jsonOption != null) {
					options.add(option(field, optionsType, option, jsonOption));
				}
			}
		}
		return new CommandMetadata(level, runner.getCommandName(), level + " " + runner.getCommandName(),
				runner.getDescription(), runner.getClass().getName(), optionsType.getName(), options);
	}

	private OptionMetadata option(Field field, Class<?> optionsType, Option option, JsonInputOption jsonOption) {
		String inputTypeRef = null;
		if (jsonOption != null) {
			inputTypeRef = referenceName(field.getGenericType());
			enqueueReferencedTypes(field.getGenericType());
		}
		return new OptionMetadata(option != null ? option.name() : jsonOption.name(),
				List.of(option != null ? option.aliases() : jsonOption.aliases()),
				option != null ? option.usage() : jsonOption.usage(), field.getGenericType().getTypeName(),
				option != null ? option.required() : jsonOption.required(),
				option != null ? option.help() : jsonOption.help(),
				option != null ? option.hidden() : jsonOption.hidden(),
				List.of(option != null ? option.depends() : jsonOption.depends()),
				List.of(option != null ? option.forbids() : jsonOption.forbids()),
				field.getDeclaringClass() != optionsType, annotations.extract(field.getAnnotations()), inputTypeRef);
	}

	private void inspectType(Class<?> type) {
		String name = type.getName();
		if (types.containsKey(name) || isScalar(type) || type.isArray() || Collection.class.isAssignableFrom(type)
				|| Map.class.isAssignableFrom(type)) {
			return;
		}
		if (type.isEnum()) {
			types.put(name, new TypeMetadata(name, "enum", description(type), null, List.of(),
					Arrays.stream(type.getEnumConstants()).map(Object::toString).toList(), null, Map.of(), List.of()));
			return;
		}

		List<FieldMetadata> fields = new ArrayList<>();
		for (Class<?> declaringType : hierarchy(type)) {
			for (Field field : declaringType.getDeclaredFields()) {
				if (include(field)) {
					fields.add(field(field));
					enqueueReferencedTypes(field.getGenericType());
				}
			}
		}

		JsonTypeInfo typeInfo = type.getAnnotation(JsonTypeInfo.class);
		JsonSubTypes subTypes = type.getAnnotation(JsonSubTypes.class);
		Map<String, String> subtypeNames = new LinkedHashMap<>();
		if (subTypes != null) {
			for (JsonSubTypes.Type subtype : subTypes.value()) {
				subtypeNames.put(subtype.name(), subtype.value().getName());
				pendingTypes.addLast(subtype.value());
			}
		}
		Class<?> superclass = type.getSuperclass();
		types.put(name,
				new TypeMetadata(name, "object", description(type),
						superclass == null || superclass == Object.class ? null : superclass.getName(), List.of(),
						List.of(), typeInfo == null ? null : typeInfo.property(), subtypeNames, fields));
	}

	private FieldMetadata field(Field field) {
		JsonProperty property = field.getAnnotation(JsonProperty.class);
		String jsonName = property == null || property.value().isBlank() ? field.getName() : property.value();
		String description = documentation.fieldDescription(field.getDeclaringClass(), field.getName());
		if (description == null) {
			warnings.add("Missing Javadoc for " + field.getDeclaringClass().getName() + "#" + field.getName());
		}
		return new FieldMetadata(field.getName(), jsonName, field.getGenericType().getTypeName(), description,
				isRequired(field.getAnnotations()), annotations.extract(field.getAnnotations()),
				referenceName(field.getGenericType()), typeArguments(field.getGenericType()));
	}

	private String description(Class<?> type) {
		String description = documentation.typeDescription(type);
		if (description == null) {
			warnings.add("Missing Javadoc for " + type.getName());
		}
		return description;
	}

	private boolean include(Field field) {
		int modifiers = field.getModifiers();
		return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()
				&& field.getAnnotation(JsonIgnore.class) == null;
	}

	private boolean isRequired(Annotation[] fieldAnnotations) {
		Set<String> required = Set.of("NotNull", "NotBlank", "NotEmpty");
		return Arrays.stream(fieldAnnotations).map(annotation -> annotation.annotationType().getSimpleName())
				.anyMatch(required::contains);
	}

	private void enqueueReferencedTypes(Type genericType) {
		if (genericType instanceof Class<?> type) {
			if (type.isArray()) {
				enqueueReferencedTypes(type.getComponentType());
			} else if (!isScalar(type)) {
				pendingTypes.addLast(type);
			}
		} else if (genericType instanceof ParameterizedType parameterizedType) {
			for (Type argument : parameterizedType.getActualTypeArguments()) {
				enqueueReferencedTypes(argument);
			}
		}
	}

	private String referenceName(Type genericType) {
		if (genericType instanceof Class<?> type) {
			return isScalar(type) ? null : type.getName();
		}
		if (genericType instanceof ParameterizedType parameterizedType) {
			for (Type argument : parameterizedType.getActualTypeArguments()) {
				String reference = referenceName(argument);
				if (reference != null) {
					return reference;
				}
			}
		}
		return null;
	}

	private List<String> typeArguments(Type genericType) {
		if (!(genericType instanceof ParameterizedType parameterizedType)) {
			return List.of();
		}
		return Arrays.stream(parameterizedType.getActualTypeArguments()).map(Type::getTypeName).toList();
	}

	private boolean isScalar(Class<?> type) {
		return type.isPrimitive() || type.getName().startsWith("java.lang.") || type.getName().startsWith("java.time.")
				|| type.getName().startsWith("java.io.") || type.getName().startsWith("java.nio.");
	}

	private List<Class<?>> hierarchy(Class<?> type) {
		List<Class<?>> hierarchy = new ArrayList<>();
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			hierarchy.add(current);
		}
		Collections.reverse(hierarchy);
		return hierarchy;
	}
}