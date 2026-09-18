package org.brts.middle.orchestration.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashDeferredLaunchGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void write_producesScriptWithHeaderCommentsInvocationsAndMessage() throws IOException {
		BashDeferredLaunchGenerator generator = new BashDeferredLaunchGenerator();
		Path descriptor = tempDir.resolve("00001-mkv-descriptor.json");
		Path bdmv = tempDir.resolve("BDMV");

		generator.comment("Title 1");
		generator.mkvToPlaylist(descriptor, bdmv);
		generator.indexWrite(tempDir.resolve("index.json"), bdmv);
		generator.message("Done.");

		Path scriptPath = generator.write(tempDir);
		String content = Files.readString(scriptPath);

		assertThat(content).startsWith("#!/usr/bin/env bash");
		assertThat(content).contains("# Title 1");
		assertThat(content).contains("$BRTS_CLI low mkv-to-playlist --descriptor " + descriptor.toAbsolutePath()
				+ " --output " + bdmv.toAbsolutePath());
		assertThat(content).contains("$BRTS_CLI low index-write --input "
				+ tempDir.resolve("index.json").toAbsolutePath() + " --output " + bdmv.toAbsolutePath());
		assertThat(content).contains("echo \"Done.\"");
		// every invocation, including index-write, now gets a trailing blank line
		assertThat(content).contains("$BRTS_CLI low index-write --input "
				+ tempDir.resolve("index.json").toAbsolutePath() + " --output " + bdmv.toAbsolutePath() + "\n\n");
		assertThat(scriptPath.toFile().canExecute()).isTrue();
	}

}
