package org.brts.common.utils.composition;

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