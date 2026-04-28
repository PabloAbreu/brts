package org.brts.common.utils.expressions;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ObjectExpressionDeserializer extends JsonDeserializer<ObjectExpression> {

	@Override
	public ObjectExpression deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		JsonToken token = p.getCurrentToken();
		if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
			return ObjectExpression.of(p.getDecimalValue());
		} else if (token == JsonToken.VALUE_STRING) {
			return ObjectExpression.expr(p.getText());
		} else {
			return ctxt.reportInputMismatch(this, "Expected number or string expression");
		}
	}

}
