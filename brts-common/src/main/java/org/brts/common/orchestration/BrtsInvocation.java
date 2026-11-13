/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/orchestration/BrtsInvocation.java' is part of BRTS.
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

package org.brts.common.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

public record BrtsInvocation(String level, String command, List<Argument> arguments) {

	public record Argument(String flag, String value) {
		public Argument {
			if (flag == null || flag.isBlank()) {
				throw new IllegalArgumentException("Invocation argument flag must not be blank");
			}
		}
	}

	public BrtsInvocation {
		if (level == null || level.isBlank()) {
			throw new IllegalArgumentException("Invocation level must not be blank");
		}
		if (command == null || command.isBlank()) {
			throw new IllegalArgumentException("Invocation command must not be blank");
		}
		arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
	}

	public static Builder builder(String level, String command) {
		return new Builder(level, command);
	}

	public List<String> toTokens() {
		List<String> tokens = new ArrayList<>();
		tokens.add(level);
		tokens.add(command);
		for (Argument argument : arguments) {
			tokens.add(argument.flag());
			if (argument.value() != null) {
				tokens.add(argument.value());
			}
		}
		return List.copyOf(tokens);
	}

	@RequiredArgsConstructor
	public static final class Builder {
		private final String level;
		private final String command;
		private final List<Argument> arguments = new ArrayList<>();

		public Builder argument(String flag, String value) {
			arguments.add(new Argument(flag, value));
			return this;
		}

		public Builder flag(String flag) {
			return argument(flag, null);
		}

		public BrtsInvocation build() {
			return new BrtsInvocation(level, command, arguments);
		}
	}
}
