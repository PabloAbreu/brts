package org.brts.middle.orchestration.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders the deferred launch as a bash script invoking the BRTS CLI, to be run later by the user.
 */
@Slf4j
public class BashDeferredLaunchGenerator extends AbstractDeferredLaunchGenerator {

	private final List<String> lines = new ArrayList<>();

	public BashDeferredLaunchGenerator() {
		lines.add("#!/usr/bin/env bash");
		lines.add("# Auto-generated middle-level orchestration script");
		lines.add("# Run each step in order to produce the low-level Blu-ray files");
		lines.add(
				"# 'java' must be in your PATH, and the BRTS CLI JAR must be at $HOME/.m2/repository/org/brts/brts-cli/1.0.0-SNAPSHOT/brts-cli-1.0.0-SNAPSHOT.jar");
		lines.add("set -euo pipefail");
		lines.add("");
		lines.add(
				"BRTS_CLI=\"java -jar $HOME/.m2/repository/org/brts/brts-cli/1.0.0-SNAPSHOT/brts-cli-1.0.0-SNAPSHOT.jar\"");
		lines.add("");
	}

	@Override
	public void comment(String text) {
		lines.add("# " + text);
	}

	@Override
	protected void invoke(BrtsCliInvocation invocation) {
		lines.add("$BRTS_CLI " + String.join(" ", invocation.toTokens()));
		lines.add("");
	}

	@Override
	public void message(String text) {
		lines.add("echo \"" + text + "\"");
	}

	@Override
	public Path write(Path outputDir) throws IOException {
		Path scriptPath = outputDir.resolve("orchestrate.sh");
		Files.writeString(scriptPath, String.join("\n", lines) + "\n");
		scriptPath.toFile().setExecutable(true);
		return scriptPath;
	}

}
