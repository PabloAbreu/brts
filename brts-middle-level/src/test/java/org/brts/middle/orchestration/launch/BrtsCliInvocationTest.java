package org.brts.middle.orchestration.launch;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/test/java/org/brts/middle/orchestration/launch/BrtsCliInvocationTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BrtsCliInvocationTest {

	@Test
	void mkvToPlaylist_producesExpectedTokens() {
		BrtsCliInvocation invocation = BrtsCliInvocation.mkvToPlaylist(Path.of("descriptor.json"),
				Path.of("/disc/BDMV").toAbsolutePath());

		assertThat(invocation.toTokens()).containsExactly("low", "mkv-to-playlist", "--descriptor",
				Path.of("descriptor.json").toAbsolutePath().toString(), "--output",
				Path.of("/disc/BDMV").toAbsolutePath().toString());
	}

	@Test
	void createTitleMenu_producesExpectedTokens() {
		BrtsCliInvocation invocation = BrtsCliInvocation.createTitleMenu(Path.of("menu-descriptor.json"),
				Path.of("/disc").toAbsolutePath(), Path.of("/media").toAbsolutePath());

		assertThat(invocation.toTokens()).containsExactly("low", "create-title-menu", "--descriptor",
				Path.of("menu-descriptor.json").toAbsolutePath().toString(), "--output",
				Path.of("/disc").toAbsolutePath().toString(), "--base-dir",
				Path.of("/media").toAbsolutePath().toString());
	}

	@Test
	void indexWrite_producesExpectedTokens() {
		BrtsCliInvocation invocation = BrtsCliInvocation.indexWrite(Path.of("index.json"),
				Path.of("/disc/BDMV").toAbsolutePath());

		assertThat(invocation.toTokens()).containsExactly("low", "index-write", "--input",
				Path.of("index.json").toAbsolutePath().toString(), "--output",
				Path.of("/disc/BDMV").toAbsolutePath().toString());
	}

	@Test
	void mobjWrite_producesExpectedTokens() {
		BrtsCliInvocation invocation = BrtsCliInvocation.mobjWrite(Path.of("MovieObject.json"),
				Path.of("/disc/BDMV").toAbsolutePath());

		assertThat(invocation.toTokens()).containsExactly("low", "mobj-write", "--input",
				Path.of("MovieObject.json").toAbsolutePath().toString(), "--output",
				Path.of("/disc/BDMV").toAbsolutePath().toString());
	}

}
