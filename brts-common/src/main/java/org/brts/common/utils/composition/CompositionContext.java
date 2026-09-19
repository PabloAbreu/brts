package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/CompositionContext.java' is part of BRTS.
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

import java.math.BigDecimal;
import java.nio.file.Path;

import org.brts.common.utils.expressions.Expression;

/** Run-time context and source for images. */
public interface CompositionContext {

	int getFrameNumber();

	Object eval(Expression expression);

	Path resolvePath(Path relativePath);

	default double evalNumeric(Expression expression, double defaultValue) {
		if (expression == null)
			return defaultValue;
		return evalNumeric(expression);
	}

	default double evalNumeric(Expression expression) {
		Object value = eval(expression);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		} else if (value == null)
			return 0;
		else {
			return new BigDecimal(value.toString()).doubleValue();
		}
	}

}
