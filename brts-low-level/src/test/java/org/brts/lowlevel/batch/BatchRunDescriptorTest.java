package org.brts.lowlevel.batch;



import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.junit.jupiter.api.Test;

class BatchRunDescriptorTest {

	@Test
	void parsesAndBuildsTokensSkippingNullValues() throws Exception {
		String json = """
				{ "steps": [
				  { "comment": "build" },
				  { "level": "low", "command": "index-write",
				    "arguments": [ { "flag": "--input", "value": "i.json" }, { "flag": "--error-details" } ] }
				] }""";
		BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(json, BatchRunDescriptor.class);

		DescriptorValidator.validateOrThrow(descriptor, "batch");
		assertThat(descriptor.getSteps().get(1).toCommandTokens()).containsExactly("index-write", "--input", "i.json",
				"--error-details");
	}

	@Test
	void rejectsEmptyAndMalformedSteps() {
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(new BatchRunDescriptor(), "batch"))
				.isInstanceOf(DescriptorValidationException.class).hasMessageContaining("steps");

		BatchStep missingLevel = new BatchStep();
		missingLevel.setCommand("index-write");
		BatchStep empty = new BatchStep();
		BatchStep argsWithoutCommand = BatchStep.comment("x");
		argsWithoutCommand.setArguments(List.of(new BatchArgument("--a", "b")));
		BatchStep blankFlag = BatchStep.invocation("low", "index-write", List.of(new BatchArgument(" ", "v")));

		for (BatchStep step : List.of(missingLevel, empty, argsWithoutCommand, blankFlag)) {
			BatchRunDescriptor descriptor = new BatchRunDescriptor();
			descriptor.getSteps().add(step);
			assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(descriptor, "batch"))
					.isInstanceOf(DescriptorValidationException.class);
		}
	}

}
