/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/TargetRootWorkspaceTest.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetRootWorkspaceTest {
	@TempDir
	Path temp;

	@Test
	void createsCanonicalTargetAndDefaultMediaDirectories() throws Exception {
		Path source = Files.createDirectory(temp.resolve("source"));
		Path target = temp.resolve("output");
		Path work = target.resolve("jobs/one");
		assertThat(TargetRootWorkspace.prepare(source, target, work)).isEqualTo(target.toRealPath());
		assertThat(work).isDirectory();
		assertThat(target.resolve("BDMV/STREAM")).isDirectory();
		assertThat(target.resolve("BDMV/CLIPINF")).isDirectory();
		assertThat(TargetRootWorkspace.canonical(work.resolve("not-yet-created")))
				.isEqualTo(work.toRealPath().resolve("not-yet-created"));
	}

	@Test
	void rejectsOverlappingRootsAndOutsideWork() throws Exception {
		Path source = Files.createDirectory(temp.resolve("source"));
		assertThatThrownBy(() -> TargetRootWorkspace.prepare(source, source.resolve("output"), null))
				.isInstanceOf(IOException.class).hasMessageContaining("separate");
		assertThatThrownBy(() -> TargetRootWorkspace.prepare(source, temp, null)).isInstanceOf(IOException.class);
		assertThatThrownBy(() -> TargetRootWorkspace.prepare(source, temp.resolve("output"), temp.resolve("outside")))
				.isInstanceOf(IOException.class).hasMessageContaining("inside target root");
		assertThat(temp.resolve("outside")).doesNotExist();
	}

	@Test
	void rejectsEscapingSymlinksIncludingDefaultPublicationDirectories() throws Exception {
		Path source = Files.createDirectory(temp.resolve("source"));
		Path target = Files.createDirectory(temp.resolve("output"));
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Files.createSymbolicLink(target.resolve("escape"), outside);
		assertThatThrownBy(() -> TargetRootWorkspace.requireInside(target, target.resolve("escape/new")))
				.isInstanceOf(IOException.class);
		Files.createDirectory(target.resolve("BDMV"));
		Files.createSymbolicLink(target.resolve("BDMV/STREAM"), source);
		assertThatThrownBy(() -> TargetRootWorkspace.prepare(source, target, null)).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsDanglingLinksRatherThanCreatingThroughThem() throws Exception {
		Path target = Files.createDirectory(temp.resolve("output"));
		Files.createSymbolicLink(target.resolve("dangling"), temp.resolve("missing"));
		assertThatThrownBy(() -> TargetRootWorkspace.directory(target, target.resolve("dangling/work")))
				.isInstanceOf(IOException.class);
		assertThat(temp.resolve("missing")).doesNotExist();
	}

	@Test
	void rejectsDifferentFilesystemWhenAvailable() throws Exception {
		Path sharedMemory = Path.of("/dev/shm");
		assumeTrue(Files.isDirectory(sharedMemory) && Files.isWritable(sharedMemory));
		assumeTrue(!Files.getFileStore(temp).equals(Files.getFileStore(sharedMemory)));
		Path target = Files.createDirectory(temp.resolve("output"));
		Files.createSymbolicLink(target.resolve("different-device"), sharedMemory);
		assertThatThrownBy(() -> TargetRootWorkspace.requireInside(target, target.resolve("different-device/file")))
				.isInstanceOf(IOException.class);
		// /proc has nested mount points under a single canonical root, exercising FileStore rather than prefix checks.
		Path proc = Path.of("/proc");
		Path mountedFile = proc.resolve("interrupts");
		if (Files.exists(mountedFile) && !Files.getFileStore(proc).equals(Files.getFileStore(mountedFile)))
			assertThatThrownBy(() -> TargetRootWorkspace.requireInside(proc, mountedFile))
					.isInstanceOf(IOException.class).hasMessageContaining("filesystem");
	}
}
