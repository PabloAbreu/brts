package org.brts.common.validation;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/validation/FilesystemConstraintsTest.java' is part of BRTS.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;
import lombok.Setter;

class FilesystemConstraintsTest {

	@TempDir
	Path tempDir;

	@Getter
	@Setter
	static class FileBean {

		@ExistingFile
		private Object value;

	}

	@Getter
	@Setter
	static class DirBean {

		@ExistingDirectory
		private Object value;

	}

	@Getter
	@Setter
	static class OutputBean {

		@WritableDirectory(createIfMissing = true)
		private Object value;

	}

	@Getter
	@Setter
	static class StrictOutputBean {

		@WritableDirectory
		private Object value;

	}

	private static Set<ConstraintViolation<Object>> violations(Object bean) {
		return DescriptorValidator.validate(bean);
	}

	@Test
	void existingFileAcceptsStringFileAndPath() throws Exception {
		Path file = Files.createFile(tempDir.resolve("source.mkv"));

		for (Object value : new Object[] { file.toString(), file.toFile(), file }) {
			FileBean bean = new FileBean();
			bean.setValue(value);
			assertThat(violations(bean)).as("value=%s", value).isEmpty();
		}
	}

	@Test
	void existingFileTreatsNullAndBlankAsValid() {
		FileBean nullBean = new FileBean();
		assertThat(violations(nullBean)).isEmpty();

		FileBean blankBean = new FileBean();
		blankBean.setValue("   ");
		assertThat(violations(blankBean)).isEmpty();
	}

	@Test
	void existingFileRejectsMissingFile() {
		FileBean bean = new FileBean();
		bean.setValue(tempDir.resolve("absent.mkv").toString());

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("file does not exist");
	}

	@Test
	void existingFileRejectsDirectory() {
		FileBean bean = new FileBean();
		bean.setValue(tempDir.toFile());

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("is not a regular file");
	}

	@Test
	void existingDirectoryRejectsRegularFile() throws Exception {
		Path file = Files.createFile(tempDir.resolve("source.mkv"));
		DirBean bean = new DirBean();
		bean.setValue(file);

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("is not a directory");
	}

	@Test
	void existingDirectoryAcceptsDirectory() {
		DirBean bean = new DirBean();
		bean.setValue(tempDir);

		assertThat(violations(bean)).isEmpty();
	}

	@Test
	void writableDirectoryAcceptsMissingDirectoryWhenCreateIfMissing() {
		OutputBean bean = new OutputBean();
		bean.setValue(new File(tempDir.toFile(), "out/nested"));

		assertThat(violations(bean)).isEmpty();
	}

	@Test
	void writableDirectoryRejectsMissingDirectoryWhenNotCreatable() {
		StrictOutputBean bean = new StrictOutputBean();
		bean.setValue(tempDir.resolve("out"));

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("directory does not exist");
	}

	@Test
	void writableDirectoryRejectsExistingRegularFile() throws Exception {
		Path file = Files.createFile(tempDir.resolve("out"));
		OutputBean bean = new OutputBean();
		bean.setValue(file);

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("exists but is not a directory");
	}

	@Test
	void malformedPathIsReported() {
		FileBean bean = new FileBean();
		bean.setValue("some\u0000path");

		assertThat(violations(bean)).singleElement().extracting(ConstraintViolation::getMessage)
				.isEqualTo("is not a valid filesystem path");
	}

}
