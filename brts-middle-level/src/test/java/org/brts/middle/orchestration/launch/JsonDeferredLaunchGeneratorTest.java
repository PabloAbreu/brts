package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/test/java/org/brts/middle/orchestration/launch/JsonDeferredLaunchGeneratorTest.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidator;
import org.brts.lowlevel.batch.BatchArgument;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonDeferredLaunchGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void write_producesValidBatchRunDescriptorWithStepsInOrder() throws Exception {
		JsonDeferredLaunchGenerator generator = new JsonDeferredLaunchGenerator();
		Path descriptor = tempDir.resolve("00001-mkv-descriptor.json");
		Path bdmv = tempDir.resolve("BDMV");

		generator.comment("Title 1");
		generator.mkvToPlaylist(descriptor, bdmv);
		generator.indexWrite(tempDir.resolve("index.json"), bdmv);
		generator.message("Done.");

		Path written = generator.write(tempDir);
		assertThat(written).isEqualTo(tempDir.resolve("orchestrate.json")).exists();
		assertThat(Files.readString(written)).doesNotContain("wellFormed").doesNotContain("invocation");

		BatchRunDescriptor read = JsonMapperFactory.get().readValue(written.toFile(), BatchRunDescriptor.class);
		DescriptorValidator.validateOrThrow(read, "generated batch");
		assertThat(read.getSteps()).containsExactly(BatchStep.comment("Title 1"),
				BatchStep.invocation("low", "mkv-to-playlist",
						List.of(new BatchArgument("--descriptor", descriptor.toAbsolutePath().toString()),
								new BatchArgument("--output", bdmv.toAbsolutePath().toString()))),
				BatchStep.invocation("low", "index-write",
						List.of(new BatchArgument("--input", tempDir.resolve("index.json").toAbsolutePath().toString()),
								new BatchArgument("--output", bdmv.toAbsolutePath().toString()))),
				BatchStep.message("Done."));
	}

}
