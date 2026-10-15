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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
		CliMetadata metadata = new CliMetadata("1.1", "1.0",
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
	}

	@Test
	void acceptsLegacyMetadataWithoutEnumChoices() throws Exception {
		OptionMetadata legacyOption = new OptionMetadata("--path", List.of(), "Path", "java.io.File", false, false,
				false, List.of(), List.of(), false, List.of(), null);
		CliMetadata legacy = new CliMetadata("1.0", "1.0",
				List.of(new LevelMetadata("low", List.of(new CommandMetadata("low", "inspect", "low inspect", "Inspect",
						"Runner", "Options", List.of(legacyOption))))),
				Map.of(), List.of());

		new BashCompletionGenerator().generate(legacy, tempDir.resolve("legacy.bash"));
		assertThat(Files.readString(tempDir.resolve("legacy.bash"))).contains("'--path'");
	}

	@Test
	void rejectsUnsupportedSchemaVersion() {
		CliMetadata unsupported = new CliMetadata("2.0", "1.0", List.of(), Map.of(), List.of());

		assertThatThrownBy(() -> new BashCompletionGenerator().generate(unsupported, tempDir.resolve("bad.bash")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2.0");
	}

	@Test
	void quotesMetadataValuesForShellSafety() throws Exception {
		CommandMetadata command = new CommandMetadata("low", "odd'command", "low odd", "Odd", "Runner", "Options",
				List.of(option("--say'hi", List.of(), "boolean", List.of(), false)));
		CliMetadata metadata = new CliMetadata("1.1", "1.0", List.of(new LevelMetadata("low", List.of(command))),
				Map.of(), List.of());
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
