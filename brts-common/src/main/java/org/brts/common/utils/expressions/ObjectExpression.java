package org.brts.common.utils.expressions;



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
