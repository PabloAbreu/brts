package org.brts.common.json;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/json/JsonMapperFactory.java' is part of BRTS.
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
