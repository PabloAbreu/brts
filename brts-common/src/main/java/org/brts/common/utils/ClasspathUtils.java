package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/ClasspathUtils.java' is part of BRTS.
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

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClasspathUtils {
	public static List<String> enumerate(String path, Predicate<String> nameFilter) throws Exception {
		// enumerate all files in the classpath under the given path, and return their
		// names as a list of strings
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		List<String> result = new ArrayList<>();
		log.debug("Enumerating classpath resources in {} with filter {}", path, nameFilter);
		cl.getResources(path).asIterator().forEachRemaining(url -> {
			log.debug("Resource {} ", url);
			try {
				switch (url.getProtocol()) {
				case "file":
					File[] children = new File(url.toURI()).listFiles(f -> f.isFile() && nameFilter.test(f.getName()));
					for (File f : children)
						result.add(f.getName());

					break;
				case "jar":
					JarURLConnection conn = (JarURLConnection) url.openConnection();
					JarFile jar = conn.getJarFile();
					jar.stream().filter(entry -> entry.getName().startsWith(path) && nameFilter.test(entry.getName()))
							.forEach(entry -> {
								log.debug("Found classpath resource: {}", entry);
								result.add(entry.getName().substring(path.length()));
							});
					break;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		return result;
	}

	public static InputStream getResourceAsStream(String path) {
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
	}
}
