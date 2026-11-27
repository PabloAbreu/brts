/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/utils/paths/BrPath.java' is part of BRTS.
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

package org.brts.common.utils.paths;

import java.nio.file.Path;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for standard Blu-Ray paths.
 *
 */
@RequiredArgsConstructor
@Slf4j
public abstract class BrPath {
	private static final String BDMV_FOLDER = "BDMV";
	private static final String CERTIFICATE_FOLDER = "CERTIFICATE";
	private static final String BACKUP_FOLDER = "BACKUP";
	private static final String BDJO_FOLDER = "BDJO";
	private static final String CLIPINF_FOLDER = "CLIPINF";
	private static final String JAR_FOLDER = "JAR";
	private static final String META_FOLDER = "META";
	private static final String PLAYLIST_FOLDER = "PLAYLIST";
	private static final String STREAM_FOLDER = "STREAM";
	private static final String INDEX_BDMV_FILE = "index.bdmv";
	private static final String MOVIE_OBJECT_BDMV_FILE = "MovieObject.bdmv";
	private static final String CLPI_EXTENSION = ".clpi";
	private static final String MPLS_EXTENSION = ".mpls";
	private static final String M2TS_EXTENSION = ".m2ts";

	private final @Getter Path path;

	protected Path subPath(String path) {
		return this.path.resolve(path);
	}

	public void createFolderStructure() {
		path.toFile().mkdirs();
	}

	public static BrRoot root(Path path) {
		return new BrRoot(path);
	}

	public static BrRoot root(Path path, boolean createFolderStructure) {
		BrRoot brRoot = new BrRoot(path);
		if (createFolderStructure) {
			log.debug("Creating Blu-Ray folder structure at {}", path);
			brRoot.createFolderStructure();
		}
		return brRoot;
	}

	public static BdmvPath bdmv(Path path) {
		return new BdmvPath(path);
	}

	public static class BrRoot extends BrPath {
		public BrRoot(Path path) {
			super(path);
		}

		public BdmvPath bdmv() {
			return new BdmvPath(subPath(BDMV_FOLDER));
		}

		public Path certificate() {
			// placebo
			return subPath(CERTIFICATE_FOLDER);
		}

		public void createFolderStructure() {
			bdmv().createFolderStructure();
			certificate().toFile().mkdirs();
		}
	}

	public static class BdmvPath extends BrPath {
		public BdmvPath(Path path) {
			super(path);
		}

		public void createFolderStructure() {
			backup().toFile().mkdirs();
			bdjo().toFile().mkdirs();
			clipinf().createFolderStructure();
			jar().toFile().mkdirs();
			meta().toFile().mkdirs();
			playlist().createFolderStructure();
			stream().createFolderStructure();
		}

		public Path backup() {
			return subPath(BACKUP_FOLDER);
		}

		public Path bdjo() {
			return subPath(BDJO_FOLDER);
		}

		public ClipinfPath clipinf() {
			return new ClipinfPath(subPath(CLIPINF_FOLDER));
		}

		public Path jar() {
			return subPath(JAR_FOLDER);
		}

		public Path meta() {
			return subPath(META_FOLDER);
		}

		public PlaylistPath playlist() {
			return new PlaylistPath(subPath(PLAYLIST_FOLDER));
		}

		public StreamPath stream() {
			return new StreamPath(subPath(STREAM_FOLDER));
		}

		public Path indexBdmv() {
			return subPath(INDEX_BDMV_FILE);
		}

		public Path MovieObjectBdmv() {
			return subPath(MOVIE_OBJECT_BDMV_FILE);
		}

		// Shortcut methods for common sub-paths
		/**
		 * Shortcut method to get the path to a specific playlist file.
		 */
		public Path playlist(String playlistName) {
			return playlist().mpls(playlistName);
		}

		/**
		 * Shortcut method to get the path to a specific clipinf file.
		 */
		public Path clipinf(String clipName) {
			return clipinf().clpi(clipName);
		}

		/**
		 * Shortcut method to get the path to a specific stream file.
		 */
		public Path stream(String streamName) {
			return stream().m2ts(streamName);
		}
	}

	public static class ClipinfPath extends BrPath {
		public ClipinfPath(Path path) {
			super(path);
		}

		public Path clpi(String clipName) {
			return subPath(clipName + CLPI_EXTENSION);
		}
	}

	public static class PlaylistPath extends BrPath {
		public PlaylistPath(Path path) {
			super(path);
		}

		public Path mpls(String playlistName) {
			return subPath(playlistName + MPLS_EXTENSION);
		}
	}

	public static class StreamPath extends BrPath {
		public StreamPath(Path path) {
			super(path);
		}

		public Path m2ts(String streamName) {
			return subPath(streamName + M2TS_EXTENSION);
		}
	}
}
