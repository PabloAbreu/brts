/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservationPublicationTest.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservationPublicationTest {
	@TempDir
	Path temp;

	@Test
	void publishesExclusivePairAndRetainsStagedInodes() throws Exception {
		Path m2ts = stage("normalized.m2ts", "stream");
		Path clpi = stage("normalized.clpi", "metadata");
		Path target = temp.resolve("00001.m2ts");
		Path targetClpi = temp.resolve("CLIPINF/00001.clpi");
		PreservationPublication.publish(temp, m2ts, clpi, target, targetClpi);
		assertThat(Files.isSameFile(m2ts, target)).isTrue();
		assertThat(Files.isSameFile(clpi, targetClpi)).isTrue();
		assertThat(temp.resolve("publication-pending.json")).doesNotExist();
		assertThatThrownBy(() -> PreservationPublication.publish(temp, m2ts, clpi, target, targetClpi))
				.isInstanceOf(IOException.class).hasMessageContaining("already exists");
	}

	@Test
	void destinationCollisionNeverPublishesFirstFile() throws Exception {
		Path m2ts = stage("normalized.m2ts", "stream");
		Path clpi = stage("normalized.clpi", "metadata");
		Path target = temp.resolve("00001.m2ts");
		Path targetClpi = temp.resolve("00001.clpi");
		Files.writeString(targetClpi, "existing");
		assertThatThrownBy(() -> PreservationPublication.publish(temp, m2ts, clpi, target, targetClpi))
				.isInstanceOf(IOException.class).hasMessageContaining("already exists");
		assertThat(target).doesNotExist();
		assertThat(Files.readString(targetClpi)).isEqualTo("existing");
	}

	@Test
	void recoversInterruptedFirstLinkWithoutRemovingStages() throws Exception {
		Path m2ts = stage("normalized.m2ts", "stream");
		Path clpi = stage("normalized.clpi", "metadata");
		Path target = temp.resolve("00001.m2ts");
		Path targetClpi = temp.resolve("00001.clpi");
		pending(m2ts, clpi, target, targetClpi);
		Files.createLink(target, m2ts);
		PreservationPublication.recover(temp, target, targetClpi);
		assertThat(target).doesNotExist();
		assertThat(targetClpi).doesNotExist();
		assertThat(m2ts).exists();
		assertThat(clpi).exists();
		assertThat(temp.resolve("publication-pending.json")).doesNotExist();
	}

	@Test
	void recoveryNeverDeletesUnrelatedReplacement() throws Exception {
		Path m2ts = stage("normalized.m2ts", "stream");
		Path clpi = stage("normalized.clpi", "metadata");
		Path target = temp.resolve("00001.m2ts");
		Path targetClpi = temp.resolve("00001.clpi");
		pending(m2ts, clpi, target, targetClpi);
		Files.writeString(target, "unrelated");
		assertThatThrownBy(() -> PreservationPublication.recover(temp, target, targetClpi))
				.isInstanceOf(IOException.class).hasMessageContaining("unrelated");
		assertThat(Files.readString(target)).isEqualTo("unrelated");
		assertThat(temp.resolve("publication-pending.json")).exists();
	}

	private Path stage(String name, String content) throws IOException {
		Path dir = temp.resolve("attempt-1");
		Files.createDirectories(dir);
		return Files.writeString(dir.resolve(name), content);
	}

	private void pending(Path m2ts, Path clpi, Path target, Path targetClpi) throws IOException {
		var pending = new PreservationPublication.Pending(m2ts.toString(), clpi.toString(), target.toString(),
				targetClpi.toString());
		JsonMapperFactory.get().writeValue(temp.resolve("publication-pending.json").toFile(), pending);
	}
}
