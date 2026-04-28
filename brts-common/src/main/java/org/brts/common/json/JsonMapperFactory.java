package org.brts.common.json;

import org.brts.common.utils.expressions.ObjectExpression;
import org.brts.common.utils.expressions.ObjectExpressionDeserializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Singleton factory providing a pre-configured Jackson {@link ObjectMapper} for all BRT JSON descriptor reading and
 * writing.
 */
public final class JsonMapperFactory {

	private static final ObjectMapper INSTANCE = createMapper();

	private JsonMapperFactory() {
	}

	private static ObjectMapper createMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new Jdk8Module());
		SimpleModule module = new SimpleModule();
		module.addDeserializer(ObjectExpression.class, new ObjectExpressionDeserializer());

		mapper.registerModule(module);
		// Human-readable output
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		// Skip null fields in output for compactness
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		// Be lenient when reading descriptors written by future versions
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return mapper;
	}

	public static ObjectMapper get() {
		return INSTANCE;
	}

}
