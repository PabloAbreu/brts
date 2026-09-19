package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/LazyValueExpression.java' is part of BRTS.
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

import org.brts.common.utils.expressions.Expression;

import jakarta.el.ELContext;
import jakarta.el.ValueExpression;
import lombok.RequiredArgsConstructor;

/**
 * A ValueExpression that lazily evaluates an Expression when requested by the EL engine.
 *
 * <p>
 * This allows variables to reference other variables that may not be defined yet at assignment time. The expression is
 * only evaluated when the EL engine calls getValue(), at which point all referenced variables should be available.
 * </p>
 *
 * <p>
 * Example: If variable "b" is set to "${a}" before "a" is defined, this class ensures "b" will correctly resolve to
 * "a"'s value when evaluated, regardless of definition order.
 * </p>
 */
@RequiredArgsConstructor
class LazyValueExpression extends ValueExpression {

	private static final long serialVersionUID = 1L;

	private final Expression expression;

	private final CompositionContextImpl context;

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getValue(ELContext elContext) {
		return (T) context.eval(expression);
	}

	@Override
	public void setValue(ELContext elContext, Object value) {
		throw new UnsupportedOperationException("Cannot set value on a lazy expression");
	}

	@Override
	public boolean isReadOnly(ELContext elContext) {
		return true;
	}

	@Override
	public Class<?> getType(ELContext elContext) {
		return Object.class;
	}

	@Override
	public Class<?> getExpectedType() {
		return Object.class;
	}

	@Override
	public String getExpressionString() {
		return expression.getExpression();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof LazyValueExpression))
			return false;
		LazyValueExpression other = (LazyValueExpression) obj;
		return expression.equals(other.expression);
	}

	@Override
	public int hashCode() {
		return expression.hashCode();
	}

	@Override
	public boolean isLiteralText() {
		return false;
	}

}
