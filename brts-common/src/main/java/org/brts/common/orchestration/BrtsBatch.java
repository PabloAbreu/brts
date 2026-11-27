/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/orchestration/BrtsBatch.java' is part of BRTS.
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

public record BrtsBatch(List<Step> preflightSteps, List<Step> steps) {

	public record Step(BrtsInvocation invocation, String comment, String message) {
		public Step {
			boolean hasInvocation = invocation != null;
			boolean hasNote = comment != null && !comment.isBlank();
			boolean hasMessage = message != null && !message.isBlank();
			if (!hasInvocation && hasNote == hasMessage) {
				throw new IllegalArgumentException("A batch step must be either an invocation or a comment/message");
			}
			if (!hasInvocation && hasNote && hasMessage) {
				throw new IllegalArgumentException("A non-invocation step cannot be both a comment and a message");
			}
		}

		public static Step invocation(BrtsInvocation invocation) {
			return new Step(Objects.requireNonNull(invocation, "invocation"), null, null);
		}

		public static Step invocation(BrtsInvocation invocation, String comment) {
			return new Step(Objects.requireNonNull(invocation, "invocation"), comment, null);
		}

		public static Step comment(String comment) {
			return new Step(null, comment, null);
		}

		public static Step message(String message) {
			return new Step(null, null, message);
		}
	}

	public BrtsBatch {
		preflightSteps = List.copyOf(Objects.requireNonNull(preflightSteps, "preflightSteps"));
		steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
		if (steps.isEmpty()) {
			throw new IllegalArgumentException("A batch must contain at least one step");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final List<Step> preflightSteps = new ArrayList<>();
		private final List<Step> steps = new ArrayList<>();

		public Builder add(BrtsInvocation invocation) {
			steps.add(Step.invocation(invocation));
			return this;
		}

		public Builder add(BrtsInvocation invocation, String comment) {
			steps.add(Step.invocation(invocation, comment));
			return this;
		}

		public Builder addComment(String comment) {
			steps.add(Step.comment(comment));
			return this;
		}

		public Builder addMessage(String message) {
			steps.add(Step.message(message));
			return this;
		}

		public Builder addPreflight(BrtsInvocation invocation) {
			preflightSteps.add(Step.invocation(invocation));
			return this;
		}

		public Builder addStep(Step step) {
			steps.add(Objects.requireNonNull(step, "step"));
			return this;
		}

		public Builder addPreflightStep(Step step) {
			preflightSteps.add(Objects.requireNonNull(step, "step"));
			return this;
		}

		public BrtsBatch build() {
			return new BrtsBatch(preflightSteps, steps);
		}
	}
}
