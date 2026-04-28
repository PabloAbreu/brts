package org.brts.common.mkv;

import java.util.List;

import org.brts.common.model.StreamCodingType;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract representation of the tracks found in a source media container (MKV, etc.). Low-level components use this
 * model to map source tracks to target Blu-ray PIDs.
 */
@Getter
@Setter
public class SourceMediaInfo {

	/** Path to the source file. */
	private String sourcePath;

	/** Duration of the media in milliseconds. */
	private long durationMs;

	private List<SourceTrack> tracks;

	// -------------------------------------------------------------------------

	/** Describes a single track within the source container. */
	@Getter
	@Setter
	public static class SourceTrack {

		/** Track number as reported by the container (1-based in MKV). */
		private int trackNumber;

		private StreamCodingType codingType;

		// --- Video ---
		private Integer widthPixels;

		private Integer heightPixels;

		private Double frameRateFps;

		// --- Audio ---
		private Integer channels;

		private Integer sampleRateHz;

		private Integer bitrateKbps;

		private String language;

		// --- Subtitle ---
		private String subtitleFormat; // e.g. "ASS", "SRT", "PGS"

		/** Codec private data (e.g. AVCDecoderConfigurationRecord for H.264). */
		private byte[] codecPrivate;

	}

}
