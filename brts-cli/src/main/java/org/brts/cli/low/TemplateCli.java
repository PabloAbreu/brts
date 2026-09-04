package org.brts.cli.low;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.cli.FeatureRunner;
import org.brts.common.template.TemplateRenderer;
import org.kohsuke.args4j.Option;

import lombok.extern.slf4j.Slf4j;

/**
 * CLI subcommand for rendering templates using FreeMarker and data sources (e.g. JSON).
 */
@Slf4j
public class TemplateCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--template", aliases = { "-t" }, required = true, usage = "Path to the template file")
		File template;

		@Option(name = "--data", aliases = { "-d" }, usage = "Path to JSON data file or inline JSON string")
		String data;

		@Option(name = "--output", aliases = { "-o" }, usage = "Output file path (if omitted, prints to stdout)")
		File output;

	}

	public static class Render extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "render-template";
		}

		@Override
		public String getDescription() {
			return "Render an output file from a template and a data model (e.g. JSON)";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			if (opts.template == null || !opts.template.exists()) {
				throw new IllegalArgumentException("Template file not found: " + opts.template);
			}

			Path templatePath = opts.template.toPath();
			String result;

			if (opts.data != null && !opts.data.isBlank()) {
				File dataFile = new File(opts.data);
				if (dataFile.exists() && dataFile.isFile()) {
					result = TemplateRenderer.getInstance().renderWithJsonFile(templatePath, dataFile.toPath());
				} else {
					result = TemplateRenderer.getInstance().renderWithJsonString(templatePath, opts.data);
				}
			} else {
				result = TemplateRenderer.getInstance().renderTemplate(templatePath, null);
			}

			if (opts.output != null) {
				Path outputPath = opts.output.toPath();
				if (outputPath.getParent() != null) {
					Files.createDirectories(outputPath.getParent());
				}
				Files.writeString(outputPath, result, StandardCharsets.UTF_8);
				log.info("Rendered template output written to {}", outputPath);
			} else {
				System.out.println(result);
			}
		}

	}

}
