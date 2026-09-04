package org.brts.common.template;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.brts.common.json.JsonMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic utility class for rendering output from templates and data models using FreeMarker. Supports rendering from
 * template files or inline strings with data models provided as Java objects, Maps, or JSON files/strings.
 */
@Slf4j
@RequiredArgsConstructor
public class TemplateRenderer {

	private static final TemplateRenderer DEFAULT_INSTANCE = new TemplateRenderer();

	@Getter
	private final Configuration configuration;

	/**
	 * Creates a TemplateRenderer with default FreeMarker configuration (UTF-8 encoding, computer number format).
	 */
	public TemplateRenderer() {
		this(createDefaultConfiguration());
	}

	/**
	 * Returns the shared default TemplateRenderer instance.
	 */
	public static TemplateRenderer getInstance() {
		return DEFAULT_INSTANCE;
	}

	public static Configuration createDefaultConfiguration() {
		Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
		cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
		cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		cfg.setLogTemplateExceptions(false);
		cfg.setWrapUncheckedExceptions(true);
		cfg.setFallbackOnNullLoopVariable(false);
		cfg.setNumberFormat("computer");
		return cfg;
	}

	/**
	 * Renders an inline template string with the provided data model.
	 */
	public String renderTemplateString(String templateContent, Object dataModel) {
		try {
			Template template = new Template("inlineTemplate", new StringReader(templateContent), configuration);
			return processTemplate(template, dataModel);
		} catch (IOException | TemplateException e) {
			throw new IllegalStateException("Failed to process inline template", e);
		}
	}

	/**
	 * Renders a template file with the provided data model.
	 */
	public String renderTemplate(Path templatePath, Object dataModel) {
		try {
			String templateContent = Files.readString(templatePath, StandardCharsets.UTF_8);
			Template template = new Template(templatePath.getFileName().toString(), new StringReader(templateContent),
					configuration);
			return processTemplate(template, dataModel);
		} catch (IOException | TemplateException e) {
			throw new IllegalStateException("Failed to process template file: " + templatePath, e);
		}
	}

	/**
	 * Renders a template file and writes the result to an output file.
	 */
	public void renderTemplateToFile(Path templatePath, Object dataModel, Path outputPath) {
		String result = renderTemplate(templatePath, dataModel);
		try {
			if (outputPath.getParent() != null) {
				Files.createDirectories(outputPath.getParent());
			}
			Files.writeString(outputPath, result, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write rendered template output to: " + outputPath, e);
		}
	}

	/**
	 * Renders a template using a JSON file as the data source.
	 */
	public String renderWithJsonFile(Path templatePath, Path jsonPath) {
		Object dataModel = loadJsonModel(jsonPath);
		return renderTemplate(templatePath, dataModel);
	}

	/**
	 * Renders a template using a JSON string as the data source.
	 */
	public String renderWithJsonString(Path templatePath, String jsonString) {
		Object dataModel = parseJsonModel(jsonString);
		return renderTemplate(templatePath, dataModel);
	}

	/**
	 * Renders an inline template using a JSON file as the data source.
	 */
	public String renderTemplateStringWithJsonFile(String templateContent, Path jsonPath) {
		Object dataModel = loadJsonModel(jsonPath);
		return renderTemplateString(templateContent, dataModel);
	}

	/**
	 * Renders an inline template using a JSON string as the data source.
	 */
	public String renderTemplateStringWithJsonString(String templateContent, String jsonString) {
		Object dataModel = parseJsonModel(jsonString);
		return renderTemplateString(templateContent, dataModel);
	}

	/**
	 * Renders a template with JSON data and writes the result to an output file.
	 */
	public void renderWithJsonToFile(Path templatePath, Path jsonPath, Path outputPath) {
		String result = renderWithJsonFile(templatePath, jsonPath);
		try {
			if (outputPath.getParent() != null) {
				Files.createDirectories(outputPath.getParent());
			}
			Files.writeString(outputPath, result, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write rendered template output to: " + outputPath, e);
		}
	}

	/**
	 * Convenience static method: render template string with data model.
	 */
	public static String render(String templateContent, Object dataModel) {
		return DEFAULT_INSTANCE.renderTemplateString(templateContent, dataModel);
	}

	/**
	 * Convenience static method: render template file with data model.
	 */
	public static String render(Path templatePath, Object dataModel) {
		return DEFAULT_INSTANCE.renderTemplate(templatePath, dataModel);
	}

	/**
	 * Convenience static method: render template file and write to output file.
	 */
	public static void renderToFile(Path templatePath, Object dataModel, Path outputPath) {
		DEFAULT_INSTANCE.renderTemplateToFile(templatePath, dataModel, outputPath);
	}

	/**
	 * Convenience static method: render template file with JSON file.
	 */
	public static String renderJson(Path templatePath, Path jsonPath) {
		return DEFAULT_INSTANCE.renderWithJsonFile(templatePath, jsonPath);
	}

	/**
	 * Convenience static method: render template file with JSON file and write to output file.
	 */
	public static void renderJsonToFile(Path templatePath, Path jsonPath, Path outputPath) {
		DEFAULT_INSTANCE.renderWithJsonToFile(templatePath, jsonPath, outputPath);
	}

	private String processTemplate(Template template, Object dataModel) throws TemplateException, IOException {
		Object modelToUse = prepareDataModel(dataModel);
		StringWriter writer = new StringWriter();
		template.process(modelToUse, writer);
		return writer.toString();
	}

	private Object prepareDataModel(Object dataModel) {
		if (dataModel == null) {
			return new HashMap<String, Object>();
		}
		if (dataModel instanceof Map) {
			return dataModel;
		}
		// If it's not already a map (e.g. a List or primitive), wrap it with common root keys
		Map<String, Object> wrapped = new HashMap<>();
		wrapped.put("data", dataModel);
		wrapped.put("root", dataModel);
		return wrapped;
	}

	private static Object loadJsonModel(Path jsonPath) {
		if (!Files.exists(jsonPath)) {
			throw new IllegalArgumentException("JSON data file not found: " + jsonPath);
		}
		try {
			String content = Files.readString(jsonPath, StandardCharsets.UTF_8);
			return parseJsonModel(content);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read JSON file: " + jsonPath, e);
		}
	}

	private static Object parseJsonModel(String jsonString) {
		if (jsonString == null || jsonString.isBlank()) {
			return new HashMap<String, Object>();
		}
		try {
			ObjectMapper mapper = JsonMapperFactory.get();
			return mapper.readValue(jsonString, Object.class);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to parse JSON data", e);
		}
	}
}
