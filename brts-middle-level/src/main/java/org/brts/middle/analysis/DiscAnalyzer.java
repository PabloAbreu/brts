/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/main/java/org/brts/middle/analysis/DiscAnalyzer.java' is part of BRTS.
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

package org.brts.middle.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchArgument;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Builds a low-level parser batch and renders browsable HTML from its JSON outputs. */
public class DiscAnalyzer {

	public static final String DESCRIPTOR_NAME = "batch-run.json";

	public static final String STYLESHEET_NAME = "disc-analyse.css";

	public record ParsedFile(Path input, Path json, Path html, String command, String kind) {
	}

	public record Diagnostic(String file, String message) {
	}

	public record Plan(Path discRoot, Path bdmvRoot, Path outputRoot, Path descriptor, BatchRunDescriptor batch,
			List<ParsedFile> files, List<Diagnostic> diagnostics) {
	}

	/** Validates the input/output roots, creates ordered parser steps, and writes the batch descriptor. */
	public Plan prepare(Path discRoot, Path outputRoot) throws IOException {
		Path normalizedDisc = discRoot.toAbsolutePath().normalize();
		Path bdmv = normalizedDisc.resolve("BDMV");
		if (!Files.isDirectory(bdmv)) {
			throw new IllegalArgumentException("Disc root must contain a BDMV directory: " + normalizedDisc);
		}

		Path normalizedOutput = outputRoot.toAbsolutePath().normalize();
		if (normalizedOutput.startsWith(normalizedDisc)) {
			throw new IllegalArgumentException(
					"Output directory must be outside the disc root/BDMV tree: " + normalizedOutput);
		}
		if (Files.exists(normalizedOutput)) {
			if (!Files.isDirectory(normalizedOutput)) {
				throw new IllegalArgumentException("Output path is not a directory: " + normalizedOutput);
			}
			try (Stream<Path> contents = Files.list(normalizedOutput)) {
				if (contents.findAny().isPresent()) {
					throw new IllegalArgumentException("Output directory must be empty: " + normalizedOutput);
				}
			}
		} else {
			Files.createDirectories(normalizedOutput);
		}

		Path outputBdmv = normalizedOutput.resolve("BDMV");
		List<ParsedFile> files = new ArrayList<>();
		List<Diagnostic> diagnostics = new ArrayList<>();
		List<BatchStep> steps = new ArrayList<>();
		addRequired(bdmv.resolve("index.bdmv"), outputBdmv.resolve("index.json"), "index-parse", "index", files, steps,
				diagnostics);
		addRequired(bdmv.resolve("MovieObject.bdmv"), outputBdmv.resolve("MovieObject.json"), "mobj-parse",
				"movieobjects", files, steps, diagnostics);
		addDirectoryFiles(bdmv.resolve("PLAYLIST"), ".mpls", "playlist-parse", "playlist", outputBdmv, files, steps,
				diagnostics);
		addDirectoryFiles(bdmv.resolve("CLIPINF"), ".clpi", "clip-parse", "clipinfo", outputBdmv, files, steps,
				diagnostics);
		addDirectoryFiles(bdmv.resolve("STREAM"), ".m2ts", "m2ts-info", "m2ts", outputBdmv, files, steps, diagnostics);

		if (steps.isEmpty()) {
			steps.add(BatchStep.message("No supported HDMV files were found to parse."));
		}
		for (ParsedFile file : files) {
			Files.createDirectories(file.json().getParent());
		}
		BatchRunDescriptor batch = new BatchRunDescriptor();
		batch.setSteps(steps);
		Path descriptor = normalizedOutput.resolve(DESCRIPTOR_NAME);
		ObjectMapper mapper = JsonMapperFactory.get();
		mapper.writeValue(descriptor.toFile(), batch);
		copyStylesheet(normalizedOutput);
		return new Plan(normalizedDisc, bdmv, normalizedOutput, descriptor, batch, List.copyOf(files),
				List.copyOf(diagnostics));
	}

	/** Writes HTML overview and one HTML page for each JSON file that was successfully produced. */
	public void render(Plan plan, List<Diagnostic> executionDiagnostics, boolean deferred) throws IOException {
		List<Diagnostic> diagnostics = new ArrayList<>(plan.diagnostics());
		diagnostics.addAll(executionDiagnostics);
		ObjectMapper mapper = JsonMapperFactory.get();
		Map<String, ParsedFile> byKindAndName = new HashMap<>();
		for (ParsedFile file : plan.files()) {
			byKindAndName.put(file.kind() + ":" + stem(file.input()), file);
		}
		List<ParsedFile> rendered = new ArrayList<>();
		for (ParsedFile file : plan.files()) {
			if (!Files.isRegularFile(file.json())) {
				continue;
			}
			JsonNode data = mapper.readTree(file.json().toFile());
			String page = renderObjectPage(plan, file, data, byKindAndName);
			Files.writeString(file.html(), page);
			rendered.add(file);
		}
		Files.writeString(plan.outputRoot().resolve("index.html"),
				renderOverview(plan, rendered, diagnostics, deferred));
	}

	private static void addRequired(Path input, Path output, String command, String kind, List<ParsedFile> files,
			List<BatchStep> steps, List<Diagnostic> diagnostics) {
		if (Files.isRegularFile(input)) {
			addFile(input, output, command, kind, files, steps);
		} else {
			diagnostics.add(new Diagnostic(input.toString(), "Expected file is missing."));
		}
	}

	private static void addDirectoryFiles(Path directory, String suffix, String command, String kind, Path outputBdmv,
			List<ParsedFile> files, List<BatchStep> steps, List<Diagnostic> diagnostics) throws IOException {
		if (!Files.isDirectory(directory)) {
			diagnostics.add(new Diagnostic(directory.toString(), "Expected directory is missing."));
			return;
		}
		try (Stream<Path> paths = Files.list(directory)) {
			List<Path> matching = paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(suffix))
					.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
			if (matching.isEmpty()) {
				diagnostics.add(new Diagnostic(directory.toString(), "No supported " + suffix + " files were found."));
			}
			for (Path input : matching) {
				Path relative = input.getParent().getFileName().resolve(stem(input) + ".json");
				addFile(input, outputBdmv.resolve(relative), command, kind, files, steps);
			}
		}
	}

	private static void addFile(Path input, Path json, String command, String kind, List<ParsedFile> files,
			List<BatchStep> steps) {
		Path html = json.resolveSibling(stem(json) + ".html");
		files.add(new ParsedFile(input.toAbsolutePath().normalize(), json, html, command, kind));
		steps.add(BatchStep.invocation("low", command,
				List.of(new BatchArgument("--input", input.toAbsolutePath().normalize().toString()),
						new BatchArgument("--output", json.toAbsolutePath().normalize().toString()))));
	}

	private static String renderOverview(Plan plan, List<ParsedFile> rendered, List<Diagnostic> diagnostics,
			boolean deferred) {
		StringBuilder html = pageStart("Blu-ray Disc Analysis", STYLESHEET_NAME);
		html.append(
				"<main><header><p class=\"eyebrow\">BRTS · DISC ANALYSIS</p><h1>Blu-ray structure</h1><p class=\"subtitle\">")
				.append(escape(plan.discRoot().toString())).append("</p></header>");
		if (deferred) {
			html.append(
					"<section class=\"notice\"><strong>Analysis prepared, not executed.</strong> Run the retained batch descriptor: <code>")
					.append(escape(plan.descriptor().toString())).append("</code></section>");
		}
		html.append("<section><div class=\"section-heading\"><h2>Parsed files</h2><span class=\"count\">")
				.append(rendered.size()).append(" / ").append(plan.files().size())
				.append("</span></div><div class=\"cards\">");
		for (ParsedFile file : plan.files()) {
			html.append("<article class=\"card\"><span class=\"tag\">").append(escape(file.kind()))
					.append("</span><h3>");
			if (rendered.contains(file)) {
				html.append("<a href=\"").append(escape(relative(plan.outputRoot().resolve("index.html"), file.html())))
						.append("\">").append(escape(file.input().getFileName().toString())).append("</a>");
			} else {
				html.append(escape(file.input().getFileName().toString()));
			}
			html.append("</h3><p>")
					.append(escape(plan.bdmvRoot().relativize(file.input()).toString().replace('\\', '/')))
					.append("</p></article>");
		}
		html.append("</div></section>");
		appendDiagnostics(html, diagnostics);
		html.append("<footer>Generated by BRTS</footer></main></body></html>");
		return html.toString();
	}

	private static String renderObjectPage(Plan plan, ParsedFile file, JsonNode data,
			Map<String, ParsedFile> byKindAndName) throws IOException {
		String css = relative(file.html(), plan.outputRoot().resolve(STYLESHEET_NAME));
		StringBuilder html = pageStart(file.input().getFileName().toString(), css);
		html.append("<main><nav><a href=\"")
				.append(escape(relative(file.html(), plan.outputRoot().resolve("index.html"))))
				.append("\">← Analysis overview</a></nav><header><p class=\"eyebrow\">").append(escape(file.kind()))
				.append("</p><h1>").append(escape(file.input().getFileName().toString()))
				.append("</h1><p class=\"subtitle\"><a href=\"").append(escape(relative(file.html(), file.json())))
				.append("\">Download parsed JSON</a></p></header>");
		appendRelations(html, plan, file, data, byKindAndName);
		html.append("<section><h2>Parsed data</h2><pre class=\"json\"><code>")
				.append(escape(JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(data)))
				.append("</code></pre></section><footer>Generated by BRTS</footer></main></body></html>");
		return html.toString();
	}

	private static void appendRelations(StringBuilder html, Plan plan, ParsedFile file, JsonNode data,
			Map<String, ParsedFile> byKindAndName) {
		html.append("<section><h2>Related objects</h2><ul class=\"relations\">");
		boolean hasRelation = false;
		if ("index".equals(file.kind())) {
			ParsedFile objects = first(plan.files(), "movieobjects");
			if (objects != null) {
				hasRelation = true;
				html.append("<li><a href=\"").append(escape(relative(file.html(), objects.html())))
						.append("\">MovieObject table</a></li>");
			}
			JsonNode titles = data.path("titles");
			for (int i = 0; i < titles.size(); i++) {
				JsonNode title = titles.get(i);
				if (title.path("objectType").asInt() == 1 && objects != null) {
					hasRelation = true;
					int objectId = title.path("hdmvObjectId").asInt();
					html.append("<li>Title ").append(i + 1).append(" → <a href=\"")
							.append(escape(relative(file.html(), objects.html()))).append("#object-").append(objectId)
							.append("\">HDMV object ").append(objectId).append("</a></li>");
				} else if (title.path("objectType").asInt() == 2) {
					hasRelation = true;
					html.append("<li>Title ").append(i + 1).append(" → BD-J object (not parsed): ")
							.append(escape(title.path("bdjObjectName").asText("unknown"))).append("</li>");
				}
			}
		} else if ("movieobjects".equals(file.kind())) {
			JsonNode objects = data.path("movieObjects");
			for (int i = 0; i < objects.size(); i++) {
				html.append("<li id=\"object-").append(i).append("\">HDMV object ").append(i).append("</li>");
				JsonNode commands = objects.get(i).path("navigationCommands");
				for (JsonNode command : commands) {
					String mnemonic = command.path("mnemonic").asText("");
					if (mnemonic.startsWith("PLAY_PL")) {
						String playlistName = String.format("%05d", command.path("operand1").asLong());
						appendLinkOrText(html, file, byKindAndName.get("playlist:" + playlistName),
								"Play playlist " + playlistName);
						hasRelation = true;
					}
				}
			}
		} else if ("playlist".equals(file.kind())) {
			JsonNode items = data.path("playItems");
			for (JsonNode item : items) {
				String clip = item.path("clipName").asText("");
				ParsedFile clipInfo = byKindAndName.get("clipinfo:" + clip);
				ParsedFile m2ts = byKindAndName.get("m2ts:" + clip);
				if (clipInfo != null) {
					appendLinkOrText(html, file, clipInfo, "Clip " + clip + " information");
					hasRelation = true;
				}
				if (m2ts != null) {
					appendLinkOrText(html, file, m2ts, "Clip " + clip + " stream");
					hasRelation = true;
				}
			}
		} else if ("clipinfo".equals(file.kind())) {
			String clip = data.path("clipName").asText(stem(file.input()));
			appendLinkOrText(html, file, byKindAndName.get("m2ts:" + clip), "M2TS stream " + clip);
			hasRelation = byKindAndName.containsKey("m2ts:" + clip);
		}
		if (!hasRelation) {
			html.append("<li class=\"muted\">No parsed related objects found.</li>");
		}
		html.append("</ul></section>");
	}

	private static void appendLinkOrText(StringBuilder html, ParsedFile from, ParsedFile to, String label) {
		html.append("<li>");
		if (to == null) {
			html.append(escape(label)).append(" (not found in this disc)");
		} else {
			html.append("<a href=\"").append(escape(relative(from.html(), to.html()))).append("\">")
					.append(escape(label)).append("</a>");
		}
		html.append("</li>");
	}

	private static ParsedFile first(List<ParsedFile> files, String kind) {
		return files.stream().filter(file -> kind.equals(file.kind())).findFirst().orElse(null);
	}

	private static void appendDiagnostics(StringBuilder html, List<Diagnostic> diagnostics) {
		html.append(
				"<section class=\"diagnostics\"><div class=\"section-heading\"><h2>Diagnostics</h2><span class=\"count\">")
				.append(diagnostics.size()).append("</span></div>");
		if (diagnostics.isEmpty()) {
			html.append("<p class=\"muted\">No missing files or execution errors.</p>");
		} else {
			html.append("<ul>");
			for (Diagnostic diagnostic : diagnostics) {
				html.append("<li><strong>").append(escape(diagnostic.file())).append("</strong>: ")
						.append(escape(diagnostic.message())).append("</li>");
			}
			html.append("</ul>");
		}
		html.append("</section>");
	}

	private static StringBuilder pageStart(String title, String cssHref) {
		return new StringBuilder(
				"<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
				.append(escape(title)).append(" · BRTS</title><link rel=\"stylesheet\" href=\"").append(escape(cssHref))
				.append("\"></head><body>");
	}

	private static String relative(Path fromFile, Path target) {
		return fromFile.getParent().relativize(target).toString().replace('\\', '/');
	}

	private static String stem(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&#39;");
	}

	private static void copyStylesheet(Path outputRoot) throws IOException {
		try (var input = DiscAnalyzer.class.getResourceAsStream("/assets/disc-analyse.css")) {
			if (input == null) {
				throw new IOException("Missing classpath stylesheet resource: /assets/disc-analyse.css");
			}
			Files.copy(input, outputRoot.resolve(STYLESHEET_NAME));
		}
	}
}
