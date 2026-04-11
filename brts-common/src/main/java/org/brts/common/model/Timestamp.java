package org.brts.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents a Blu-ray 90 kHz timestamp (used in CLPI and MPLS). The Blu-ray spec uses a
 * 45 kHz clock for some fields and 90 kHz for others; all values here are in 90 kHz
 * units.
 */
public final class Timestamp {

	public static final long TICKS_PER_SECOND = 90_000L;

	/**
	 * MPLS PlayItem IN_time / OUT_time and PlayMark timestamps use the 45 kHz STC clock,
	 * not the 90 kHz PTS clock.
	 */
	public static final long MPLS_TICKS_PER_SECOND = 45_000L;

	private final long ticks;

	@JsonCreator
	public Timestamp(long ticks) {
		if (ticks < 0)
			throw new IllegalArgumentException("Timestamp ticks must be >= 0");
		this.ticks = ticks;
	}

	public static Timestamp ofTicks(long ticks) {
		return new Timestamp(ticks);
	}

	public static Timestamp ofSeconds(double seconds) {
		return new Timestamp(Math.round(seconds * TICKS_PER_SECOND));
	}

	@JsonValue
	public long getTicks() {
		return ticks;
	}

	public double toSeconds() {
		return (double) ticks / TICKS_PER_SECOND;
	}

	@Override
	public String toString() {
		long totalSeconds = ticks / TICKS_PER_SECOND;
		long h = totalSeconds / 3600;
		long m = (totalSeconds % 3600) / 60;
		long s = totalSeconds % 60;
		long ms = (ticks % TICKS_PER_SECOND) * 1000 / TICKS_PER_SECOND;
		return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Timestamp))
			return false;
		return ticks == ((Timestamp) o).ticks;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(ticks);
	}

}
