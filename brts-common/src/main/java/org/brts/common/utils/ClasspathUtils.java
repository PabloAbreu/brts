package org.brts.common.utils;

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
