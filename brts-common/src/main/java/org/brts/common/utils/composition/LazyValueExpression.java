package org.brts.common.utils.composition;

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
