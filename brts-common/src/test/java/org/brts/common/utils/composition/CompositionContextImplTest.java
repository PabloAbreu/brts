package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/utils/composition/CompositionContextImplTest.java' is part of BRTS.
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

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.utils.expressions.ObjectExpression;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

// we use LinkedHashMap to guarantee insertion/iteration order, to test out-of-order variable definitions
class CompositionContextImplTest {

	@Test
	void testEvaluation() throws Exception {
		// tests only expressions
		ImagesComposition config = new ImagesComposition();
		config.setBaseImageId("base");
		Map<String, ObjectExpression> constants = new LinkedHashMap<>();
		constants.put("a", ObjectExpression.of(100));
		constants.put("b", ObjectExpression.expr("${a+100}"));
		constants.put("c", ObjectExpression.expr("${a+b+10}"));
		constants.put("d", ObjectExpression.expr("${basePath}/${frameNumber*2}"));
		config.setConstants(constants);
		CompositionContextImpl context = new CompositionContextImpl(50, config, Path.of("src/test/resources"));
		int b = (int) context.evalNumeric(ObjectExpression.expr("${b}"));

		assertThat(b).isEqualTo(200);
		assertThat(context.evalNumeric(ObjectExpression.expr("${c}"))).isEqualTo(310);
		assertThat((String) context.eval(ObjectExpression.expr("${d}"))).endsWith("src/test/resources/100");
	}

	@Test
	void testOutOfOrderVariableResolution() throws Exception {
		// Test that variables can reference other variables defined later
		ImagesComposition config = new ImagesComposition();
		config.setBaseImageId("base");
		// Define b = "${a}" before a is defined
		Map<String, ObjectExpression> constants = new LinkedHashMap<>();
		constants.put("b", ObjectExpression.expr("${a}"));
		constants.put("a", ObjectExpression.of(100));
		config.setConstants(constants);
		CompositionContextImpl context = new CompositionContextImpl(1, config, Path.of("src/test/resources"));

		// b should resolve to a's value even though b was defined first
		assertThat(context.evalNumeric(ObjectExpression.expr("${b}"))).isEqualTo(100);
		assertThat(context.evalNumeric(ObjectExpression.expr("${a}"))).isEqualTo(100);
	}

	@Test
	void testTransitiveDependencies() throws Exception {
		// Test that variables can reference variables that reference other variables
		ImagesComposition config = new ImagesComposition();
		config.setBaseImageId("base");
		// Define c -> b -> a, but in reverse order
		Map<String, ObjectExpression> constants = new LinkedHashMap<>();
		constants.put("c", ObjectExpression.expr("${b*2}"));
		constants.put("b", ObjectExpression.expr("${a+10}"));
		constants.put("a", ObjectExpression.of(5));
		config.setConstants(constants);
		CompositionContextImpl context = new CompositionContextImpl(1, config, Path.of("src/test/resources"));

		// c = (a + 10) * 2 = (5 + 10) * 2 = 30
		assertThat(context.evalNumeric(ObjectExpression.expr("${c}"))).isEqualTo(30);
		assertThat(context.evalNumeric(ObjectExpression.expr("${b}"))).isEqualTo(15);
		assertThat(context.evalNumeric(ObjectExpression.expr("${a}"))).isEqualTo(5);
	}

	@Test
	void testRuntimeEvaluation() throws Exception {
		// Test that variables can be set at runtime and reference other variables
		ImagesComposition config = new ImagesComposition();
		config.setBaseImageId("base");
		Map<String, ObjectExpression> constants = new LinkedHashMap<>();
		constants.put("x", ObjectExpression.expr("${y*2}"));
		constants.put("z", ObjectExpression.expr("${x+10}"));
		constants.put("y", ObjectExpression.of(20));
		config.setConstants(constants);
		CompositionContextImpl context = new CompositionContextImpl(5, config, Path.of("src/test/resources"));

		assertThat(context.evalNumeric(ObjectExpression.expr("${x+10}"))).isEqualTo(50);
		assertThat(context.evalNumeric(ObjectExpression.expr("${x+10 * frameNumber}"))).isEqualTo(90);
	}

	@Test
	void yetAnotherTest() throws Exception {
		// Test that variables can be set at runtime and reference other variables
		ImagesComposition config = new ImagesComposition();
		config.setBaseImageId("base");
		Map<String, ObjectExpression> constants = new LinkedHashMap<>();
		/*
		 * "constants" : { "x_space" : 300, "x0" : 200, "x1" : "${x0+x_space}" },
		 */
		constants.put("x_space", ObjectExpression.of(300));
		constants.put("x0", ObjectExpression.of(200));
		constants.put("x1", ObjectExpression.expr("${x0+x_space}"));
		config.setConstants(constants);
		CompositionContextImpl context = new CompositionContextImpl(5, config, Path.of("src/test/resources"));

		assertThat(context.eval(ObjectExpression.expr("${x0}"))).isEqualTo(200);
		assertThat(context.eval(ObjectExpression.expr("${x1}"))).isEqualTo(500L);
	}
}
