/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/WholeM2tsConverter.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/**
 * Container-aware conversion boundary; intentionally distinct from the raw elementary-stream {@code VideoConverter}.
 */
public interface WholeM2tsConverter {

	Engine engine();

	/** Writes only metadata descriptors; never extracts, encodes or muxes. */
	M2tsConversionPlan prepare(M2tsConversionRequest request) throws IOException;

	/** Prepares and executes the same batch used by deferred runs. */
	M2tsConversionResult convert(M2tsConversionRequest request) throws IOException;

	@FunctionalInterface
	interface BatchExecutor {
		void execute(Path descriptor) throws IOException;
	}

	record M2tsConversionRequest(M2tsShrinkInventory inventory, M2tsShrinkPlan shrinkPlan, M2tsShrinkConfig config,
			Path destinationRoot, Path workspace, String clipId) {
	}

	record M2tsConversionPlan(Engine engine, long targetBytes, int videoBitrateKbps, Path batchDescriptor,
			Path muxDescriptor, Path outputPath, Path outputClpi, Path workspace, List<String> warnings) {
		public M2tsConversionPlan {
			warnings = List.copyOf(warnings);
		}
	}

	record M2tsConversionResult(Path outputPath, Path outputClpi, long outputBytes, M2tsConversionPlan plan) {
	}
}
