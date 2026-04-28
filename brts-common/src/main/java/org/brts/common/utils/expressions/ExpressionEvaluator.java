package org.brts.common.utils.expressions;

public interface ExpressionEvaluator {

	Object eval(Expression expression, Object context);

	default double evalNumeric(Expression expression, Object context) {
		Object value = eval(expression, context);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		} else {
			throw new IllegalArgumentException("Expected numeric value, got: " + value);
		}
	}

}
