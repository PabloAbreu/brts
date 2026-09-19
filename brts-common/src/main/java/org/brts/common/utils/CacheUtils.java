package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/CacheUtils.java' is part of BRTS.
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Utility methods for creating common cache structures.
 */
public class CacheUtils {

	private CacheUtils() {
	}

	/**
	 * Creates an LRU-ordered {@link LinkedHashMap} that evicts the eldest entry when {@code size > capacity}.
	 *
	 * @param <K>      key type
	 * @param <V>      value type
	 * @param capacity maximum number of entries to retain
	 * @return a new, mutable LRU map
	 */
	public static <K, V> LinkedHashMap<K, V> lruCache(int capacity) {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > capacity;
			}
		};
	}

	/**
	 * Creates an LRU-ordered {@link LinkedHashMap} that evicts the eldest entry when {@code size > capacity}, invoking
	 * {@code onEvict} on the evicted value before removal.
	 *
	 * @param <K>      key type
	 * @param <V>      value type
	 * @param capacity maximum number of entries to retain
	 * @param onEvict  callback invoked with the evicted value immediately before it is removed
	 * @return a new, mutable LRU map
	 */
	public static <K, V> LinkedHashMap<K, V> lruCache(int capacity, Consumer<V> onEvict) {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				if (size() > capacity) {
					onEvict.accept(eldest.getValue());
					return true;
				}
				return false;
			}
		};
	}
}
