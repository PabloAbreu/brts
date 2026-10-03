package org.brts.lowlevel.batch;



import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of a {@link BatchRunDescriptor}: either a BRTS CLI invocation ({@code level} + {@code command} +
 * {@code arguments}), or a pure {@code comment} / {@code message} step. An invocation may also carry a comment.
 */
@Data
@NoArgsConstructor
public class BatchStep {

	/** Free-form note, logged when the step is reached. */
	private String comment;

	/** User-facing notice printed to standard output when the step is reached. */
	private String message;

	/** CLI level ({@code low}, {@code mid} or {@code high}). */
	private String level;

	/** Command name within the level, e.g. {@code mkv-to-playlist}. */
	private String command;

	/** Ordered flag/value pairs passed to the command. */
	private List<@Valid BatchArgument> arguments;

	public static BatchStep invocation(String level, String command, List<BatchArgument> arguments) {
		BatchStep step = new BatchStep();
		step.setLevel(level);
		step.setCommand(command);
		step.setArguments(new ArrayList<>(arguments));
		return step;
	}

	public static BatchStep comment(String text) {
		BatchStep step = new BatchStep();
		step.setComment(text);
		return step;
	}

	public static BatchStep message(String text) {
		BatchStep step = new BatchStep();
		step.setMessage(text);
		return step;
	}

	@JsonIgnore
	public boolean isInvocation() {
		return command != null || level != null;
	}

	@JsonIgnore
	@AssertTrue(message = "a step must define both 'level' and 'command', or be a non-blank 'comment'/'message' step without 'arguments'")
	public boolean isWellFormed() {
		if (isInvocation()) {
			return !isBlank(level) && !isBlank(command);
		}
		return (arguments == null || arguments.isEmpty()) && (!isBlank(comment) || !isBlank(message));
	}

	/** Command tokens (without the level): command, then each flag and its non-null value. */
	public List<String> toCommandTokens() {
		List<String> tokens = new ArrayList<>();
		tokens.add(command);
		if (arguments != null) {
			for (BatchArgument argument : arguments) {
				tokens.add(argument.getFlag());
				if (argument.getValue() != null) {
					tokens.add(argument.getValue());
				}
			}
		}
		return tokens;
	}

	/** Human-readable form, e.g. {@code low mkv-to-playlist --descriptor a.json}. */
	public String describe() {
		if (!isInvocation()) {
			return !isBlank(message) ? "message" : "comment";
		}
		return level + " " + String.join(" ", toCommandTokens());
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
