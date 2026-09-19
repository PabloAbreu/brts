package org.brts.common.test.sampledata;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/test/sampledata/RequiresSamplesCondition.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

final class RequiresSamplesCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		Optional<Path> root = Samples.configuredRoot();
		if (root.isEmpty()) {
			return ConditionEvaluationResult.disabled("System property 'test.samples.dir' is not set or empty");
		}

		Path samplesRoot = root.get();
		if (!Files.isDirectory(samplesRoot)) {
			return ConditionEvaluationResult
					.disabled("Samples directory does not exist: " + samplesRoot.toAbsolutePath());
		}

		List<String> requiredPaths = requiredPaths(context);
		Optional<Path> missingPath = requiredPaths.stream().map(Samples::sample).filter(path -> !Files.exists(path))
				.findFirst();
		if (missingPath.isPresent()) {
			return ConditionEvaluationResult
					.disabled("Required sample path does not exist: " + missingPath.get().toAbsolutePath());
		}

		return ConditionEvaluationResult.enabled("Samples are available at " + samplesRoot.toAbsolutePath());
	}

	private List<String> requiredPaths(ExtensionContext context) {
		List<String> requiredPaths = new ArrayList<>();
		context.getTestClass().map(testClass -> testClass.getAnnotation(RequiresSamples.class))
				.map(RequiresSamples::value).ifPresent(paths -> requiredPaths.addAll(Arrays.asList(paths)));
		context.getElement().map(element -> element.getAnnotation(RequiresSamples.class)).map(RequiresSamples::value)
				.ifPresent(paths -> requiredPaths.addAll(Arrays.asList(paths)));
		return requiredPaths;
	}
}
