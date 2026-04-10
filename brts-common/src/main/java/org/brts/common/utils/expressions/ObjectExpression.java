package org.brts.common.utils.expressions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObjectExpression implements Expression {
    private Object value;
    private String expression;

    public static ObjectExpression of(Object value) {
        return new ObjectExpression(value, null);
    }

    public static ObjectExpression expr(String expression) {
        return new ObjectExpression(null, expression);
    }
}
