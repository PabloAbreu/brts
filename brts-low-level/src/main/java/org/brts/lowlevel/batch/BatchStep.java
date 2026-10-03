package org.brts.lowlevel.batch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/batch/BatchStep.java' is part of BRTS.
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
