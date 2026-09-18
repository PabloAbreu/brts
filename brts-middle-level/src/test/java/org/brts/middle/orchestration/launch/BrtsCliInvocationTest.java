package org.brts.middle.orchestration.launch;

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
