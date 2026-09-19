package org.brts.common.utils.expressions;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/expressions/ObjectExpression.java' is part of BRTS.
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model for an object expression, which can hold a value or an expression string. The expression can be evaluated to
 * produce the value.
 *
 * Example usage:
 *
 * <pre>
 * ObjectExpression valueExpr = ObjectExpression.of(42);
 * ObjectExpression expr = ObjectExpression.expr("2 + 2");
 * </pre>
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObjectExpression implements Expression {

	/**
	 * The value held by this expression. Can be any object.
	 */
	private Object value;

	/**
	 * The expression represented by this object expression. Can be any string. Will be evaluated to produce the value
	 * of this expression.
	 */
	private String expression;

	public static ObjectExpression of(Object value) {
		return new ObjectExpression(value, null);
	}

	public static ObjectExpression expr(String expression) {
		return new ObjectExpression(null, expression);
	}

}
