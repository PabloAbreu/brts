package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/BdmvStructureGenerator.java' is part of BRTS.
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

import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.brts.lowlevel.writer.MoviePlaylistWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates the complete Blu-ray disc folder structure from the low-level model objects.
 * <p>
 * Output layout:
 *
 * <pre>
 * &lt;outputRoot&gt;/
 *   BDMV/
 *     index.bdmv
 *     MovieObject.bdmv
 *     BACKUP/
 *       index.bdmv       (identical copy)
 *       MovieObject.bdmv (identical copy)
 *     PLAYLIST/
 *       00001.mpls  ...
 *     CLIPINF/
 *       00001.clpi  ...
 *     STREAM/
 *       00001.m2ts  ...  (not generated here — written by M2tsWriter)
 *     META/
 *       DL/              (placeholder, empty)
 *   CERTIFICATE/
 *     BACKUP/            (placeholder, empty)
 * </pre>
 */
@Slf4j
public class BdmvStructureGenerator {

	private final ClipInfoWriter clipInfoWriter = new ClipInfoWriter();

	private final MoviePlaylistWriter playlistWriter = new MoviePlaylistWriter();

	private final IndexBdmvWriter indexWriter = new IndexBdmvWriter();

	private final MovieObjectsWriter movieObjectsWriter = new MovieObjectsWriter();

	/**
	 * Creates the full Blu-ray directory skeleton at {@code outputRoot}. Call {@link #writeClip},
	 * {@link #writePlaylist} etc. to populate content.
	 */
	public void initStructure(Path outputRoot) throws IOException {
		log.info("Initialising BDMV structure at {}", outputRoot);
		createDir(outputRoot.resolve("BDMV/PLAYLIST"));
		createDir(outputRoot.resolve("BDMV/CLIPINF"));
		createDir(outputRoot.resolve("BDMV/STREAM"));
		createDir(outputRoot.resolve("BDMV/BACKUP/PLAYLIST"));
		createDir(outputRoot.resolve("BDMV/BACKUP/CLIPINF"));
		createDir(outputRoot.resolve("BDMV/META/DL"));
		createDir(outputRoot.resolve("CERTIFICATE/BACKUP"));
	}

	public void writeIndex(Path outputRoot, IndexBdmv index) throws IOException {
		Path out = outputRoot.resolve("BDMV/index.bdmv");
		indexWriter.write(index, out);
		indexWriter.write(index, outputRoot.resolve("BDMV/BACKUP/index.bdmv"));
		log.info("Wrote {}", out);
	}

	public void writeMovieObjects(Path outputRoot, MovieObjects objects) throws IOException {
		Path out = outputRoot.resolve("BDMV/MovieObject.bdmv");
		movieObjectsWriter.write(objects, out);
		movieObjectsWriter.write(objects, outputRoot.resolve("BDMV/BACKUP/MovieObject.bdmv"));
		log.info("Wrote {}", out);
	}

	public void writePlaylist(Path outputRoot, String name, MoviePlaylist playlist) throws IOException {
		Path out = outputRoot.resolve("BDMV/PLAYLIST/" + name + ".mpls");
		playlistWriter.write(playlist, out);
		playlistWriter.write(playlist, outputRoot.resolve("BDMV/BACKUP/PLAYLIST/" + name + ".mpls"));
		log.info("Wrote {}", out);
	}

	public void writeClip(Path outputRoot, String name, ClipInfo clip) throws IOException {
		Path out = outputRoot.resolve("BDMV/CLIPINF/" + name + ".clpi");
		clipInfoWriter.write(clip, out);
		clipInfoWriter.write(clip, outputRoot.resolve("BDMV/BACKUP/CLIPINF/" + name + ".clpi"));
		log.info("Wrote {}", out);
	}

	private void createDir(Path dir) throws IOException {
		Files.createDirectories(dir);
	}

}
