package org.brts.common.test.sampledata;

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