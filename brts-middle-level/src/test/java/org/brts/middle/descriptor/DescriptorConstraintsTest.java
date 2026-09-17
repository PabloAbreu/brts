package org.brts.middle.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
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

}
