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
			return new BdmvPath(subPath("BDMV"));
		}

		public Path certificate() {
			// placebo
			return subPath("CERTIFICATE");
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
			return subPath("BACKUP");
		}

		public Path bdjo() {
			return subPath("BDJO");
		}

		public ClipinfPath clipinf() {
			return new ClipinfPath(subPath("CLIPINF"));
		}

		public Path jar() {
			return subPath("JAR");
		}

		public Path meta() {
			return subPath("META");
		}

		public PlaylistPath playlist() {
			return new PlaylistPath(subPath("PLAYLIST"));
		}

		public StreamPath stream() {
			return new StreamPath(subPath("STREAM"));
		}

		public Path indexBdmv() {
			return subPath("index.bdmv");
		}

		public Path MovieObjectBdmv() {
			return subPath("MovieObject.bdmv");
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
			return subPath(clipName + ".clpi");
		}
	}

	public static class PlaylistPath extends BrPath {
		public PlaylistPath(Path path) {
			super(path);
		}

		public Path mpls(String playlistName) {
			return subPath(playlistName + ".mpls");
		}
	}

	public static class StreamPath extends BrPath {
		public StreamPath(Path path) {
			super(path);
		}

		public Path m2ts(String streamName) {
			return subPath(streamName + ".m2ts");
		}
	}
}
