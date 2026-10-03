/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/test/java/org/brts/highlevel/style/StyleTemplateLoaderTest.java' is part of BRTS.
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

package org.brts.highlevel.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.sources.synth.SVGImageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StyleTemplateLoaderTest {

	private static final Path REPO_ASSETS = Path.of("../assets").toAbsolutePath().normalize();

	@TempDir
	Path tmp;

	@Test
	void loadsInstalledSciFiTemplateWithAbsolutePaths() throws IOException {
		StyleTemplate template = new StyleTemplateLoader(() -> List.of(REPO_ASSETS)).load("brts:sci-fi");

		assertThat(template.directory()).isEqualTo(REPO_ASSETS.resolve("styles/sci-fi"));
		assertThat(template.manifest().getName()).isEqualTo("sci-fi");
		assertThat(template.manifest().getStyle().getSelectedColor()).isEqualTo("#00F0FF");
		assertThat(template.hasTitleMenuBackground()).isTrue();

		ImageReference bg = template.manifest().getTitleMenu().getBackgroundSource().getComposition().getImages()
				.get(0);
		assertThat(Path.of(bg.getSyntheticImage().getSrcPath())).isAbsolute().exists();

		ImageReference banner = template.manifest().getPopupMenu().getBackgrounds().getShared().get(0).getSource();
		assertThat(Path.of(banner.getSyntheticImage().getSrcPath()))
				.isEqualTo(template.directory().resolve("popup-menu-banner.svg"));
		assertThat(template.manifest().getPopupMenu().getBackgrounds().getAudio().get(0).getSource().getSyntheticImage()
				.getSrcPath()).startsWith(template.directory().toString());
	}

	@Test
	void sciFiScreenBackgroundRendersAtFullHd() throws IOException {
		StyleTemplate template = new StyleTemplateLoader(() -> List.of(REPO_ASSETS)).load("brts:sci-fi");
		ImageReference bg = template.manifest().getTitleMenu().getBackgroundSource().getComposition().getImages()
				.get(0);

		SVGImageGenerator generator = new SVGImageGenerator(bg.getSyntheticImage());
		try {
			ImageFrame frame = generator.generate(0, null);
			assertThat(frame.width()).isEqualTo(1920);
			assertThat(frame.height()).isEqualTo(1080);
		} finally {
			generator.close();
		}
	}

	@Test
	void installedLookupUsesFirstRootContainingTemplate() throws IOException {
		Path empty = tmp.resolve("empty");
		Files.createDirectories(empty);
		Path root = tmp.resolve("root");
		writeManifest(root.resolve("styles/mine"), "{\"name\":\"mine\"}");

		StyleTemplate template = new StyleTemplateLoader(() -> List.of(empty, root)).load("brts:mine");

		assertThat(template.directory()).isEqualTo(root.resolve("styles/mine").toAbsolutePath().normalize());
		assertThat(template.hasTitleMenuBackground()).isFalse();
	}

	@Test
	void loadsCustomPathAndResolvesRelativeAssets() throws IOException {
		Path dir = tmp.resolve("custom");
		writeManifest(dir, """
				{"titleMenu": {"backgroundSource": {"imagePath": "bg.png", "audioPath": "music.ac3",
				  "durationSeconds": 10}}}
				""");
		Files.writeString(dir.resolve("bg.png"), "x");
		Files.writeString(dir.resolve("music.ac3"), "x");

		StyleTemplate template = new StyleTemplateLoader(List::of).load(dir.toString());

		var bg = template.manifest().getTitleMenu().getBackgroundSource();
		assertThat(bg.getImagePath()).isEqualTo(dir.resolve("bg.png").toString());
		assertThat(bg.getAudioPath()).isEqualTo(dir.resolve("music.ac3").toString());
	}

	@Test
	void rejectsMissingAsset() throws IOException {
		Path dir = tmp.resolve("broken");
		writeManifest(dir, "{\"titleMenu\": {\"backgroundSource\": {\"imagePath\": \"missing.png\"}}}");

		assertThatThrownBy(() -> new StyleTemplateLoader(List::of).load(dir.toString()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing.png");
	}

	@Test
	void rejectsUnknownInstalledTemplate() {
		assertThatThrownBy(() -> new StyleTemplateLoader(() -> List.of(tmp)).load("brts:nope"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'nope' not found");
	}

	@Test
	void rejectsPathTraversalInInstalledName() {
		assertThatThrownBy(() -> new StyleTemplateLoader(() -> List.of(tmp)).load("brts:../etc"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid installed");
	}

	@Test
	void rejectsFolderWithoutManifest() throws IOException {
		Files.createDirectories(tmp.resolve("nomanifest"));
		assertThatThrownBy(() -> new StyleTemplateLoader(List::of).load(tmp.resolve("nomanifest").toString()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining(StyleTemplateLoader.MANIFEST_FILE);
	}

	private static void writeManifest(Path dir, String json) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(StyleTemplateLoader.MANIFEST_FILE), json);
	}

}
