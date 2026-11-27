/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/FeatureRunnerCmdLineParser.java' is part of BRTS.
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

package org.brts.cli;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.annotation.Annotation;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MethodSetter;
import org.kohsuke.args4j.spi.Setters;

import lombok.RequiredArgsConstructor;

class FeatureRunnerCmdLineParser extends CmdLineParser {

	public FeatureRunnerCmdLineParser(Object bean) {
		super(bean);
		// no manage custom annotations
		// support for @JsonInputOption
		parseJsonInputOption(bean);
	}

	@RequiredArgsConstructor
	private static class JsonInputOptionAsOption implements Option {

		private final JsonInputOption o;

		@Override
		public String name() {
			return o.name();
		}

		@Override
		public String usage() {
			return o.usage();
		}

		@Override
		public boolean required() {
			return o.required();
		}

		@Override
		public String[] aliases() {
			return o.aliases();
		}

		@Override
		public String metaVar() {
			return o.metaVar();
		}

		@Override
		public boolean help() {
			return o.help();
		}

		@Override
		public boolean hidden() {
			return o.hidden();
		}

		@Override
		public String[] depends() {
			return o.depends();
		}

		@Override
		public String[] forbids() {
			return o.forbids();
		}

		@Override
		public Class<JsonInputOptionHandler> handler() {
			return JsonInputOptionHandler.class;
		}

		@Override
		public Class<? extends Annotation> annotationType() {
			return Option.class;
		}

	}

	protected void parseJsonInputOption(Object bean) {
		// recursively process all the methods/fields.
		for (Class<?> c = bean.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				JsonInputOption o = m.getAnnotation(JsonInputOption.class);
				if (o != null)
					addOption(new MethodSetter(this, bean, m), new JsonInputOptionAsOption(o));
			}

			for (Field f : c.getDeclaredFields()) {
				JsonInputOption o = f.getAnnotation(JsonInputOption.class);
				if (o != null)
					addOption(Setters.create(f, bean), new JsonInputOptionAsOption(o));
			}
		}
	}

}
