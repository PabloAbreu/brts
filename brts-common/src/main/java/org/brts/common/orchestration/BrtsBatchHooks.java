/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/orchestration/BrtsBatchHooks.java' is part of BRTS.
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

public interface BrtsBatchHooks {

	enum FailureAction {
		STOP, CONTINUE
	}

	@FunctionalInterface
	interface StepExecutor {
		void execute(BrtsInvocation invocation) throws Exception;
	}

	record StepContext(BrtsBatch.Step step, int index, int total, boolean preflight) {
	}

	default void beforeBatch(BrtsBatch batch) {
	}

	default void beforePreflight(int total) {
	}

	default void beforeResume(int firstStep, int total) {
	}

	default int resumeFrom(BrtsBatch batch, int requestedFirstStep) {
		return requestedFirstStep;
	}

	default void beforeStep(StepContext context) {
	}

	default void executeStep(StepContext context, StepExecutor executor) throws Exception {
		if (context.step().invocation() != null) {
			executor.execute(context.step().invocation());
		}
	}

	default void afterStep(StepContext context) {
	}

	default FailureAction onFailure(StepContext context, Exception failure) {
		return FailureAction.STOP;
	}

	default void afterBatch(BrtsBatchResult result) {
	}
}
