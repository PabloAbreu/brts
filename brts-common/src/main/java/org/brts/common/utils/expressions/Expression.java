package org.brts.common.utils.expressions;

public interface Expression {
    Object getValue();

    String getExpression();

    default boolean isValue() {
        return getExpression() == null;
    }
}
