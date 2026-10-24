/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/M2tsShrinkCliTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.brts.cli.LowLevelDispatcher;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class M2tsShrinkCliTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "1234", "123456", "abcde", " 0001" })
	void rejectsInvalidClipIds(String clipId) {
		var options = new M2tsShrinkCli.Options();
		options.clipId = clipId;
		options.targetBytes = 1;
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(options, "options"))
				.isInstanceOf(DescriptorValidationException.class)
				.hasMessageContaining("--clip-id must contain exactly five digits");
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, Long.MIN_VALUE })
	void rejectsNonPositiveTargetBytes(long targetBytes) {
		var options = new M2tsShrinkCli.Options();
		options.clipId = "00001";
		options.targetBytes = targetBytes;
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(options, "options"))
				.isInstanceOf(DescriptorValidationException.class)
				.hasMessageContaining("--target-bytes must be positive");
	}

	@Test
	void acceptsValidClipIdAndPositiveTargetBytes() {
		var options = new M2tsShrinkCli.Options();
		options.clipId = "00001";
		options.targetBytes = 1;
		DescriptorValidator.validateOrThrow(options, "options");
	}

	@Test
	void commandIsRegisteredAtLowLevel() {
		assertThat(LowLevelDispatcher.getLevelDispatcher().getRunners())
				.anyMatch(runner -> runner.getCommandName().equals("m2ts-shrink"));
	}

	@Test
	void helpExplainsPlanOnlyBehaviorAndRequiredInputs() {
		var runner = new M2tsShrinkCli.Shrink();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		runner.printUsage(new PrintStream(bytes));
		String usage = bytes.toString();
		assertThat(usage).contains("plan only").contains("--disc-root").contains("--clip-id").contains("--config")
				.contains("--target-bytes").contains("--engine");
	}
}
