package org.brts.common.utils;

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
