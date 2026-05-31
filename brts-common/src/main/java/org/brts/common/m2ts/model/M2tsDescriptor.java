package org.brts.common.m2ts.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * JSON descriptor used to drive the M2TS muxer.
 * <p>
 * Describes the set of elementary stream files to mux together and any chapter timestamps to embed as EP_map anchors /
 * MPLS PlayMarks.
 * <p>
 * Example {@code 00001.m2ts-descriptor.json}:
 *
 * <pre>{@code
 * {
 *   "outputName": "00001",
 *   "streams": [
 *     { "file": "/tmp/video.h264", "pid": 4113, "streamTypeByte": 27 },
 *     { "file": "/tmp/audio.ac3",  "pid": 4352, "streamTypeByte": 129, "language": "fra" },
 *     { "file": "/tmp/sub.pgs",    "pid": 4608, "streamTypeByte": 144, "language": "fra" }
 *   ],
 *   "chapters": [
 *     { "index": 0, "ptsTicks": 0 },
 *     { "index": 1, "ptsTicks": 27000000 }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class M2tsDescriptor {

	/**
	 * Base name for the output M2TS file (5 digits, no extension). The writer will produce {@code <outputName>.m2ts} in
	 * the target directory.
	 */
	private String outputName;

	/** Elementary stream entries to mux. */
	private List<StreamEntry> streams;

	/**
	 * Chapter anchors (optional). The first chapter should have {@code ptsTicks=0} and correspond to the first IDR
	 * frame.
	 */
	private List<M2tsChapter> chapters;

	/**
	 * Target mux rate for CBR null-packet stuffing, in kbps. If {@code null}, the writer defaults to 30 000 kbps (30
	 * Mbps), which is suitable for standard BD-Video content. Use 48 000 for high-bitrate discs.
	 */
	private Integer targetBitrateKbps;

	/**
	 * Initial presentation time offset in 90 kHz ticks added to all PTS/DTS values in the output stream. This is the
	 * T-STD / HRD initial delay: the decoder must pre-buffer this much data before it starts to decode the first frame.
	 * <p>
	 * Set this to at least the VBV buffer duration of the video elementary stream (e.g.
	 * {@code videoVbvBufferBytes * 8 * 90_000 / bitrateKbps / 1000}) so that the first IDR frame and subsequent
	 * I-frames can be fully delivered at the transport stream bitrate before their DTS is reached.
	 * <p>
	 * If {@code null} or ≤ 0, no initial delay is added (legacy behaviour, may cause "Packet corrupt" warnings for
	 * large I-frames at low bitrates).
	 */
	private Long initialPtsOffsetTicks;

	/**
	 * Minimum end PTS (90 kHz ticks) that the muxer must reach by emitting PCR + null packets after all elementary
	 * stream data is written. This extends the clip's timeline beyond its actual content duration.
	 * <p>
	 * Used for out-of-mux IGS clips that must span the same PTS range as the associated background video clip so that
	 * strict players (e.g. PowerDVD) can locate the overlay.
	 * <p>
	 * If {@code null} or ≤ 0, no timeline extension is performed.
	 */
	private Long minEndPtsTicks;

	// -------------------------------------------------------------------------

	/** One elementary stream contribution to the output M2TS. */
	@Getter
	@Setter
	public static class StreamEntry {

		/** Path to the elementary stream file on disk. */
		private String file;

		/**
		 * Target PID in the MPEG-2 TS. Blu-ray convention: video 0x1011 (4113), audio 0x1100+ (4352+), PGS 0x1200+
		 * (4608+).
		 */
		private int pid;

		/**
		 * ISO 13818-1 stream_type byte for the PMT descriptor. E.g. 0x1B (27) for H.264, 0x81 (129) for AC3, 0x90 (144)
		 * for PGS.
		 */
		private int streamTypeByte;

		/** ISO 639-2 language tag (audio and subtitle streams). */
		private String language;

		private Double frameRateFps;

		/** Blu-ray video format code (1=480i, 2=480p, 3=720p, 4=1080i, 6=1080p). */
		private Integer videoFormat;

		// audio metadata
		private Integer sampleRateHz;

		private Integer bitrateKbps;

		private Integer channels;

	}

}
