package org.brts.middle.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/test/java/org/brts/middle/descriptor/DescriptorConstraintsTest.java' is part of BRTS.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.brts.middle.menu.descriptor.AudioMenuItem;
import org.brts.middle.menu.descriptor.SubtitleMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jakarta.validation.ConstraintViolation;

class DescriptorConstraintsTest {

	@TempDir
	Path tempDir;

	private SimpleBuildDescriptor validDescriptor() throws Exception {
		Path mkv = Files.createFile(tempDir.resolve("movie.mkv"));

		SimpleBuildDescriptor descriptor = new SimpleBuildDescriptor();
		descriptor.setDiscName("My Movie");
		descriptor.setOutputFolder(tempDir.resolve("out").toString());
		descriptor.setSourceMkv(mkv.toString());
		descriptor.setAudioLanguages(List.of("eng", "fra"));
		descriptor.setChapters(List.of(chapter(0), chapter(3600)));
		return descriptor;
	}

	private static TitleDescriptor.ChapterMarker chapter(double seconds) {
		TitleDescriptor.ChapterMarker marker = new TitleDescriptor.ChapterMarker();
		marker.setTimeSeconds(seconds);
		return marker;
	}

	@Test
	void validDescriptorHasNoViolations() throws Exception {
		assertThat(DescriptorValidator.validate(validDescriptor())).isEmpty();
	}

	@Test
	void reportsAllProblemsAtOnce() throws Exception {
		SimpleBuildDescriptor descriptor = validDescriptor();
		descriptor.setDiscName("  ");
		descriptor.setSourceMkv(tempDir.resolve("absent.mkv").toString());
		descriptor.setAudioLanguages(List.of("english"));
		descriptor.setChapters(List.of(chapter(-1)));

		assertThat(DescriptorValidator.validate(descriptor)).extracting(v -> v.getPropertyPath().toString())
				.containsExactlyInAnyOrder("discName", "sourceMkv", "audioLanguages[0].<list element>",
						"chapters[0].timeSeconds");
	}

	@Test
	void discDescriptorRequiresTitleMenuConfigWhenTopMenuEnabled() throws Exception {
		DiscDescriptor disc = validDescriptor().toDiscDescriptor();
		disc.setHasTopMenu(true);

		assertThat(DescriptorValidator.validate(disc)).extracting(ConstraintViolation::getMessage)
				.containsExactly("titleMenuConfig is required when hasTopMenu is true");
	}

	@Test
	void discDescriptorRequiresAtLeastOneTitle() throws Exception {
		DiscDescriptor disc = validDescriptor().toDiscDescriptor();
		disc.setTitles(List.of());

		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(disc, "disc descriptor"))
				.isInstanceOf(DescriptorValidationException.class).hasMessageContaining("Invalid disc descriptor")
				.hasMessageContaining("titles");
	}

	@Test
	void nestedTitleViolationsAreCascaded() throws Exception {
		DiscDescriptor disc = validDescriptor().toDiscDescriptor();
		disc.getTitles().get(0).setSourceMkv(tempDir.resolve("absent.mkv").toString());

		assertThat(DescriptorValidator.validate(disc)).extracting(v -> v.getPropertyPath().toString())
				.containsExactly("titles[0].sourceMkv");
	}

	@Test
	void titleMenuRejectsMultipleAudioDefaults() throws Exception {
		DiscDescriptor disc = validDescriptor().toDiscDescriptor();
		TitleMenuConfig menu = new TitleMenuConfig();
		AudioMenuItem first = new AudioMenuItem();
		first.setDefaultStream(true);
		AudioMenuItem second = new AudioMenuItem();
		second.setDefaultStream(true);
		menu.setAudioItems(List.of(first, second));
		disc.setTitleMenuConfig(menu);

		assertThat(DescriptorValidator.validate(disc)).extracting(ConstraintViolation::getMessage)
				.contains("title menu audioItems may contain at most one defaultStream");
	}

	@Test
	void titleMenuRejectsMultipleSubtitleDefaults() throws Exception {
		DiscDescriptor disc = validDescriptor().toDiscDescriptor();
		TitleMenuConfig menu = new TitleMenuConfig();
		SubtitleMenuItem first = new SubtitleMenuItem();
		first.setDefaultStream(true);
		SubtitleMenuItem second = new SubtitleMenuItem();
		second.setDefaultStream(true);
		menu.setSubtitleItems(List.of(first, second));
		disc.setTitleMenuConfig(menu);

		assertThat(DescriptorValidator.validate(disc)).extracting(ConstraintViolation::getMessage)
				.contains("title menu subtitleItems may contain at most one defaultStream");
	}

}
