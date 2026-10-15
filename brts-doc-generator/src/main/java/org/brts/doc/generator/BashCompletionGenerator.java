/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-doc-generator/src/main/java/org/brts/doc/generator/BashCompletionGenerator.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.LevelMetadata;
import org.brts.doc.metadata.OptionMetadata;

/** Generates Bash programmable completion for the metadata-described {@code brts} CLI. */
public final class BashCompletionGenerator {

	private static final List<String> SUPPORTED_SCHEMA_VERSIONS = List.of("1.0", "1.1");

	public void generate(CliMetadata metadata, Path outputFile) throws IOException {
		if (!SUPPORTED_SCHEMA_VERSIONS.contains(metadata.schemaVersion())) {
			throw new IllegalArgumentException("Unsupported CLI metadata schema version: " + metadata.schemaVersion());
		}
		Path parent = outputFile.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(outputFile, script(metadata), StandardCharsets.UTF_8);
	}

	String script(CliMetadata metadata) {
		StringBuilder script = new StringBuilder("# Bash completion for BRTS CLI. Generated from CLI metadata.\n")
				.append("_brts_contains() {\n").append("  local needle=$1 candidate\n  shift\n")
				.append("  for candidate in \"$@\"; do\n    [[ $candidate == \"$needle\" ]] && return 0\n  done\n")
				.append("  return 1\n}\n\n").append("_brts_complete() {\n")
				.append("  local cur level command token waiting='' value_prefix='' attached=0 i candidate\n")
				.append("  local -a levels=() commands=() options=() value_options=() path_options=() enum_options=() enum_values=() matches=()\n")
				.append("  COMPREPLY=()\n").append("  cur=${COMP_WORDS[COMP_CWORD]:-}\n")
				.append("  # Bash treats '=' as a word break by default; recover an attached --option=value token.\n")
				.append("  if [[ ${COMP_LINE:0:COMP_POINT} == *[[:space:]]* ]]; then\n")
				.append("    token=${COMP_LINE:0:COMP_POINT}\n    token=${token##*[[:space:]]}\n")
				.append("    [[ $token == *=* ]] && cur=$token\n  fi\n").append("  levels=(");
		for (LevelMetadata level : metadata.levels()) {
			script.append(' ').append(shellQuote(level.name()));
		}
		script.append(" )\n").append("  if (( COMP_CWORD <= 1 )); then\n")
				.append("    mapfile -t COMPREPLY < <(compgen -W \"${levels[*]}\" -- \"$cur\")\n    return\n  fi\n")
				.append("  level=${COMP_WORDS[1]:-}\n").append("  case $level in\n");
		for (LevelMetadata level : metadata.levels()) {
			script.append("    ").append(shellQuote(level.name())).append(") commands=(");
			for (CommandMetadata command : level.commands()) {
				script.append(' ').append(shellQuote(command.name()));
			}
			script.append(" ) ;;\n");
		}
		script.append("  esac\n").append("  if (( COMP_CWORD <= 2 )); then\n")
				.append("    mapfile -t COMPREPLY < <(compgen -W \"${commands[*]}\" -- \"$cur\")\n    return\n  fi\n")
				.append("  command=${COMP_WORDS[2]:-}\n").append("  case $level:$command in\n");
		for (LevelMetadata level : metadata.levels()) {
			for (CommandMetadata command : level.commands()) {
				appendCommandCase(script, level, command);
			}
		}
		script.append("  esac\n").append("  for ((i=3; i<COMP_CWORD; i++)); do\n")
				.append("    token=${COMP_WORDS[i]}\n")
				.append("    if [[ -n $waiting ]]; then waiting=''; continue; fi\n")
				.append("    if [[ $token == *=* ]]; then\n").append("      candidate=${token%%=*}\n")
				.append("      _brts_contains \"$candidate\" \"${value_options[@]}\" && continue\n").append("    fi\n")
				.append("    if _brts_contains \"$token\" \"${value_options[@]}\"; then waiting=$token; fi\n")
				.append("  done\n").append("  value_prefix=$cur\n").append("  if [[ $cur == *=* ]]; then\n")
				.append("    candidate=${cur%%=*}\n")
				.append("    if _brts_contains \"$candidate\" \"${value_options[@]}\"; then\n")
				.append("      waiting=$candidate; value_prefix=${cur#*=}; attached=1\n    fi\n  fi\n")
				.append("  if [[ -n $waiting ]]; then\n")
				.append("    if _brts_contains \"$waiting\" \"${enum_options[@]}\"; then\n")
				.append("      case $level:$command:$waiting in\n");
		for (LevelMetadata level : metadata.levels()) {
			for (CommandMetadata command : level.commands()) {
				for (OptionMetadata option : command.options()) {
					if (!option.hidden() && !option.enumValues().isEmpty()) {
						script.append("        ").append(shellQualifiedOptionPatterns(level, command, option))
								.append(") enum_values=(");
						for (String enumValue : option.enumValues()) {
							script.append(' ').append(shellQuote(enumValue));
						}
						script.append(" ) ;;\n");
					}
				}
			}
		}
		script.append("      esac\n")
				.append("      mapfile -t matches < <(compgen -W \"${enum_values[*]}\" -- \"$value_prefix\")\n")
				.append("    elif _brts_contains \"$waiting\" \"${path_options[@]}\"; then\n")
				.append("      mapfile -t matches < <(compgen -f -- \"$value_prefix\")\n").append("    fi\n")
				.append("    if (( attached )); then\n      for candidate in \"${matches[@]}\"; do COMPREPLY+=(\"$waiting=$candidate\"); done\n")
				.append("    else COMPREPLY=(\"${matches[@]}\"); fi\n    return\n  fi\n")
				.append("  mapfile -t COMPREPLY < <(compgen -W \"${options[*]}\" -- \"$cur\")\n")
				.append("}\n\ncomplete -F _brts_complete brts\n");
		return script.toString();
	}

	private void appendCommandCase(StringBuilder script, LevelMetadata level, CommandMetadata command) {
		script.append("    ").append(shellQuote(level.name() + ":" + command.name())).append(")\n")
				.append("      options=(");
		for (OptionMetadata option : command.options()) {
			if (!option.hidden()) {
				appendOptionNames(script, option);
			}
		}
		script.append(" )\n      value_options=(");
		for (OptionMetadata option : command.options()) {
			if (!option.hidden() && !isBoolean(option.javaType())) {
				appendOptionNames(script, option);
			}
		}
		script.append(" )\n      path_options=(");
		for (OptionMetadata option : command.options()) {
			if (!option.hidden() && isPath(option.javaType())) {
				appendOptionNames(script, option);
			}
		}
		script.append(" )\n      enum_options=(");
		for (OptionMetadata option : command.options()) {
			if (!option.hidden() && !option.enumValues().isEmpty()) {
				appendOptionNames(script, option);
			}
		}
		script.append(" ) ;;\n");
	}

	private void appendOptionNames(StringBuilder script, OptionMetadata option) {
		script.append(' ').append(shellQuote(option.name()));
		for (String alias : option.aliases()) {
			script.append(' ').append(shellQuote(alias));
		}
	}

	private String shellQualifiedOptionPatterns(LevelMetadata level, CommandMetadata command, OptionMetadata option) {
		List<String> patterns = new ArrayList<>();
		patterns.add(shellQuote(level.name() + ":" + command.name() + ":" + option.name()));
		for (String alias : option.aliases()) {
			patterns.add(shellQuote(level.name() + ":" + command.name() + ":" + alias));
		}
		return String.join("|", patterns);
	}

	private static boolean isBoolean(String javaType) {
		return "boolean".equals(javaType) || "java.lang.Boolean".equals(javaType);
	}

	private static boolean isPath(String javaType) {
		return "java.io.File".equals(javaType) || "java.nio.file.Path".equals(javaType);
	}

	private static String shellQuote(String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}
}
