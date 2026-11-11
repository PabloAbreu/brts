/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/TargetRootWorkspace.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;

/** Fail-closed placement policy for target-side media and persistent extraction workspaces. */
public final class TargetRootWorkspace {

	private TargetRootWorkspace() {
	}

	/** Resolves existing ancestors, including symbolic links, without requiring the final path to exist. */
	public static Path canonical(Path path) throws IOException {
		if (path == null)
			throw new IOException("A workspace path is required");
		Path cursor = path.toAbsolutePath().normalize();
		var missing = new ArrayDeque<Path>();
		while (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
			if (cursor.getParent() == null)
				throw new IOException("No existing ancestor for " + path);
			missing.addFirst(cursor.getFileName());
			cursor = cursor.getParent();
		}
		cursor = cursor.toRealPath();
		for (Path component : missing)
			cursor = cursor.resolve(component);
		return cursor;
	}

	/** Creates target directories only after ruling out overlap with the source tree. */
	public static Path prepare(Path sourceRoot, Path outputRoot, Path workDir) throws IOException {
		Path source = canonical(sourceRoot);
		Path target = canonical(outputRoot);
		if (!Files.isDirectory(source))
			throw new IOException("Source root must be a directory: " + source);
		if (target.startsWith(source) || source.startsWith(target))
			throw new IOException("Source and output roots must be separate, non-overlapping directories");
		Files.createDirectories(target);
		if (!canonical(outputRoot).equals(target) || !Files.isDirectory(target))
			throw new IOException("Output root changed while preparing workspace: " + outputRoot);
		if (workDir != null)
			directory(target, workDir);
		directory(target, target.resolve("BDMV/STREAM"));
		directory(target, target.resolve("BDMV/CLIPINF"));
		return target;
	}

	/** Requires canonical containment AND the target root's actual filesystem, even for nonexistent children. */
	public static Path requireInside(Path root, Path path) throws IOException {
		Path target = canonical(root);
		Path resolved = canonical(path);
		if (!Files.isDirectory(target) || !resolved.startsWith(target))
			throw new IOException("Workspace path must remain inside target root " + target + ": " + path);
		Path ancestor = resolved;
		while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS))
			ancestor = ancestor.getParent();
		if (!Files.getFileStore(target).equals(Files.getFileStore(ancestor)))
			throw new IOException("Workspace path must use the target root filesystem: " + path);
		return resolved;
	}

	/** Validates both before and after directory creation. */
	public static void directory(Path root, Path path) throws IOException {
		Path expected = requireInside(root, path);
		Files.createDirectories(expected);
		if (!requireInside(root, path).equals(expected) || !Files.isDirectory(expected))
			throw new IOException("Workspace directory changed during creation: " + path);
	}
}
