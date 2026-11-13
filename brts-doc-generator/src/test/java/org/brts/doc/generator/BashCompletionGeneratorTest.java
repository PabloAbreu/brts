/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-doc-generator/src/test/java/org/brts/doc/generator/BashCompletionGeneratorTest.java' is part of BRTS.
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

package org.brts.doc.generator;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.brts.doc.metadata.AnnotationMetadata;
import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.LevelMetadata;
import org.brts.doc.metadata.OptionMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashCompletionGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void generatesCompletionForCommandsOptionsEnumsAndPaths() throws Exception {
		OptionMetadata mode = option("--mode", List.of("-m"), "java.lang.String", List.of("FAST", "SLOW"), false);
		OptionMetadata input = option("--input", List.of(), "java.nio.file.Path", List.of(), false);
		OptionMetadata verbose = option("--verbose", List.of("-v"), "boolean", List.of(), false);
		OptionMetadata hidden = option("--secret", List.of(), "java.lang.String", List.of("NOPE"), true);
		CommandMetadata scan = new CommandMetadata("low", "scan", "low scan", "Scan", "Runner", "Options",
				List.of(mode, input, verbose, hidden));
		CommandMetadata other = new CommandMetadata("high", "build", "high build", "Build", "Runner", "Options",
				List.of(option("--mode", List.of(), "java.lang.String", List.of("OTHER"), false)));
		CliMetadata metadata = new CliMetadata("1.0",
				List.of(new LevelMetadata("low", List.of(scan)), new LevelMetadata("high", List.of(other))), Map.of(),
				List.of());
		Path completion = tempDir.resolve("subdir/brts.bash");

		new BashCompletionGenerator().generate(metadata, completion);
		String script = Files.readString(completion);

		assertThat(script).contains("complete -F _brts_complete brts", "'low'", "'scan'", "'--mode'", "'-m'",
				"'--input'", "'--verbose'", "'FAST'", "'SLOW'").doesNotContain("--secret", "'NOPE'");
		assertThat(completion).exists();
		Process syntaxCheck = new ProcessBuilder("bash", "-n", completion.toString()).redirectErrorStream(true).start();
		String diagnostics = new String(syntaxCheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(syntaxCheck.waitFor()).withFailMessage(diagnostics).isZero();
		Files.createFile(tempDir.resolve("sample.mkv"));
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low scan --mode F); COMP_CWORD=4; COMP_LINE='brts low scan --mode F'; "
						+ "COMP_POINT=${#COMP_LINE}; _brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactly("FAST");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low scan --mode=F); COMP_CWORD=3; COMP_LINE='brts low scan --mode=F'; "
						+ "COMP_POINT=${#COMP_LINE}; _brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactly("--mode=FAST");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low scan --input s); COMP_CWORD=4; COMP_LINE='brts low scan --input s'; "
						+ "COMP_POINT=${#COMP_LINE}; _brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.contains("sample.mkv");
		Files.createDirectory(tempDir.resolve("samples"));
		assertThat(runCompletion(completion,
				"compopt() { printf 'compopt %s\\n' \"$*\"; }; "
						+ "COMP_WORDS=(brts low scan --input samples); COMP_CWORD=4; "
						+ "COMP_LINE='brts low scan --input samples'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactly("compopt -o filenames", "samples");
		assertThat(runCompletion(completion,
				"compopt() { printf 'compopt %s\\n' \"$*\"; }; "
						+ "COMP_WORDS=(brts low scan --input = samples); COMP_CWORD=5; "
						+ "COMP_LINE='brts low scan --input=samples'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactly("compopt -o filenames", "samples");
	}

	@Test
	void completesJsonInputAnnotationsWithOnlyJsonFilesAndDirectories() throws Exception {
		OptionMetadata descriptor = new OptionMetadata("--descriptor", List.of("-d"), "Descriptor", "Descriptor", false,
				false, false, List.of(), List.of(), false,
				List.of(new AnnotationMetadata("org.brts.cli.JsonInputOption", Map.of())), null);
		CommandMetadata command = new CommandMetadata("low", "write", "low write", "Write", "Runner", "Options",
				List.of(descriptor));
		CliMetadata metadata = new CliMetadata("1.0", List.of(new LevelMetadata("low", List.of(command))), Map.of(),
				List.of());
		Path completion = tempDir.resolve("completion.bash");
		new BashCompletionGenerator().generate(metadata, completion);
		Files.createFile(tempDir.resolve("sample.json"));
		Files.createFile(tempDir.resolve("sample file.json"));
		Files.createFile(tempDir.resolve("sample.txt"));
		Files.createFile(tempDir.resolve("sample.JSON"));
		Files.createDirectory(tempDir.resolve("sample folder"));
		Files.createDirectory(tempDir.resolve("sample.json.dir"));
		Files.createDirectory(tempDir.resolve("directory.json"));
		Files.createFile(tempDir.resolve("sample folder/nested.json"));
		Files.createFile(tempDir.resolve("sample folder/nested.txt"));

		for (String optionName : List.of("--descriptor", "-d")) {
			assertThat(runCompletion(completion,
					"COMP_WORDS=(brts low write " + optionName + " ''); COMP_CWORD=4; " + "COMP_LINE='brts low write "
							+ optionName + " '; COMP_POINT=${#COMP_LINE}; "
							+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
					.containsExactlyInAnyOrder("sample.json", "sample file.json", "sample folder", "sample.json.dir",
							"directory.json");
		}
		assertThat(runCompletion(completion,
				"compopt() { printf 'compopt %s\\n' \"$*\"; }; "
						+ "COMP_WORDS=(brts low write --descriptor=sample); COMP_CWORD=3; "
						+ "COMP_LINE='brts low write --descriptor=sample'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactlyInAnyOrder("compopt -o filenames", "--descriptor=sample.json",
						"--descriptor=sample file.json", "--descriptor=sample folder", "--descriptor=sample.json.dir");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low write --descriptor = sample); COMP_CWORD=5; "
						+ "COMP_LINE='brts low write --descriptor=sample'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactlyInAnyOrder("sample.json", "sample file.json", "sample folder", "sample.json.dir");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low write --descriptor 'sample folder/n'); COMP_CWORD=4; "
						+ "COMP_LINE='brts low write --descriptor sample\\ folder/n'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.containsExactly("sample folder/nested.json");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low write --descriptor missing); COMP_CWORD=4; "
						+ "COMP_LINE='brts low write --descriptor missing'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.isEmpty();
	}

	@Test
	void ignoresHiddenAndUnannotatedJsonInputs() throws Exception {
		OptionMetadata hidden = new OptionMetadata("--hidden-json", List.of("-j"), "Hidden", "Descriptor", false, false,
				true, List.of(), List.of(), false,
				List.of(new AnnotationMetadata("org.brts.cli.JsonInputOption", Map.of())), null);
		OptionMetadata unannotated = new OptionMetadata("--unannotated", List.of(), "Descriptor", "Descriptor", false,
				false, false, List.of(), List.of(), false,
				List.of(new AnnotationMetadata("example.JsonInputOption", Map.of())), "Descriptor");
		CommandMetadata command = new CommandMetadata("low", "write", "low write", "Write", "Runner", "Options",
				List.of(hidden, unannotated));
		CliMetadata metadata = new CliMetadata("1.0", List.of(new LevelMetadata("low", List.of(command))), Map.of(),
				List.of());
		Path completion = tempDir.resolve("completion.bash");
		new BashCompletionGenerator().generate(metadata, completion);
		Files.createFile(tempDir.resolve("sample.json"));

		assertThat(Files.readString(completion)).doesNotContain("--hidden-json", "'-j'");
		assertThat(runCompletion(completion,
				"COMP_WORDS=(brts low write --unannotated s); COMP_CWORD=4; "
						+ "COMP_LINE='brts low write --unannotated s'; COMP_POINT=${#COMP_LINE}; "
						+ "_brts_complete; printf '%s\\n' \"${COMPREPLY[@]}\""))
				.isEmpty();
	}

	@Test
	void generatesCompletionWithoutEnumChoices() throws Exception {
		OptionMetadata pathOption = new OptionMetadata("--path", List.of(), "Path", "java.io.File", false, false, false,
				List.of(), List.of(), false, List.of(), null);
		CliMetadata metadata = new CliMetadata("1.0",
				List.of(new LevelMetadata("low", List.of(new CommandMetadata("low", "inspect", "low inspect", "Inspect",
						"Runner", "Options", List.of(pathOption))))),
				Map.of(), List.of());

		new BashCompletionGenerator().generate(metadata, tempDir.resolve("completion.bash"));
		assertThat(Files.readString(tempDir.resolve("completion.bash"))).contains("'--path'");
	}

	@Test
	void quotesMetadataValuesForShellSafety() throws Exception {
		CommandMetadata command = new CommandMetadata("low", "odd'command", "low odd", "Odd", "Runner", "Options",
				List.of(option("--say'hi", List.of(), "boolean", List.of(), false)));
		CliMetadata metadata = new CliMetadata("1.0", List.of(new LevelMetadata("low", List.of(command))), Map.of(),
				List.of());
		Path completion = tempDir.resolve("escaped.bash");

		new BashCompletionGenerator().generate(metadata, completion);
		Process syntaxCheck = new ProcessBuilder("bash", "-n", completion.toString()).redirectErrorStream(true).start();

		String diagnostics = new String(syntaxCheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(syntaxCheck.waitFor()).withFailMessage(diagnostics).isZero();
		assertThat(Files.readString(completion)).contains("'odd'\\''command'", "'--say'\\''hi'");
	}

	private static OptionMetadata option(String name, List<String> aliases, String javaType, List<String> enumValues,
			boolean hidden) {
		return new OptionMetadata(name, aliases, "Option", javaType, false, false, hidden, List.of(), List.of(), false,
				List.of(), null, enumValues);
	}

	private List<String> runCompletion(Path completion, String setup) throws Exception {
		String harness = "source \"$1\"; " + setup;
		Process process = new ProcessBuilder("bash", "-c", harness, "bash", completion.toString())
				.directory(tempDir.toFile()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).withFailMessage(output).isZero();
		return output.lines().filter(line -> !line.isEmpty()).toList();
	}
}
