package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/test/java/org/brts/cli/middle/SimpleBuildCliValidationTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.middle.descriptor.SimpleBuildDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that descriptor problems are reported before {@code execute} is reached.
 */
class SimpleBuildCliValidationTest {

	@TempDir
	Path tempDir;

	/** Runner that fails the test if it is ever reached, and surfaces validation errors instead of exiting. */
	private static class NonExecutingRun extends SimpleBuildCli.Run {

		boolean executed;

		void check(SimpleBuildCli.SimpleBuildOptions opts) {
			validateOptions(opts);
		}

		@Override
		protected void execute(SimpleBuildCli.SimpleBuildOptions opts) {
			executed = true;
		}

	}

	private Path writeDescriptor(String json) throws Exception {
		Path file = tempDir.resolve("descriptor.json");
		Files.writeString(file, json);
		return file;
	}

	@Test
	void invalidDescriptorIsRejectedBeforeExecution() throws Exception {
		Path descriptor = writeDescriptor("""
				{
				  "discName": "",
				  "sourceMkv": "/definitely/missing/movie.mkv"
				}
				""");
		NonExecutingRun runner = new NonExecutingRun();
		SimpleBuildCli.SimpleBuildOptions opts = new SimpleBuildCli.SimpleBuildOptions();
		opts.descriptor = JsonMapperFactory.get().readValue(descriptor.toFile(), SimpleBuildDescriptor.class);
		opts.outputDir = tempDir.resolve("out");

		assertThatThrownBy(() -> runner.check(opts)).isInstanceOf(DescriptorValidationException.class)
				.hasMessageContaining("simple-build").hasMessageContaining("descriptor.discName")
				.hasMessageContaining("descriptor.sourceMkv").hasMessageContaining("file does not exist");

		assertThat(runner.executed).isFalse();
	}

	@Test
	void validDescriptorPassesValidation() throws Exception {
		Path mkv = Files.createFile(tempDir.resolve("movie.mkv"));
		NonExecutingRun runner = new NonExecutingRun();
		SimpleBuildCli.SimpleBuildOptions opts = new SimpleBuildCli.SimpleBuildOptions();
		opts.descriptor = new SimpleBuildDescriptor();
		opts.descriptor.setDiscName("My Movie");
		opts.descriptor.setOutputFolder(tempDir.resolve("bd").toString());
		opts.descriptor.setSourceMkv(mkv.toString());
		opts.outputDir = tempDir.resolve("out");

		runner.check(opts);

		assertThat(runner.executed).isFalse();
	}

}
