package org.brts.lowlevel.model.clpi;

import lombok.Getter;
import lombok.Setter;
import org.brts.common.model.StreamCodingType;

/**
 * Describes a single elementary stream within a CLPI clip program. Maps to the
 * {@code Clip_stream_type} section of the CLPI binary format.
 */
@Getter
@Setter
public class ClipStream {

	/** PID of this elementary stream in the MPEG-2 TS. */
	private int pid;

	private StreamCodingType codingType;

	// --- Video-specific ---

	/**
	 * Video format (0x01=480i, 0x02=576i, 0x03=480p, 0x04=1080i, 0x05=720p, 0x06=1080p,
	 * 0x07=576p).
	 */
	private Integer videoFormat;

	/**
	 * Frame rate (0x01=24000/1001, 0x02=24, 0x03=25, 0x04=30000/1001, 0x06=50,
	 * 0x07=60000/1001).
	 */
	private Integer frameRate;

	/** Aspect ratio (0x02=4:3, 0x03=16:9). */
	private Integer aspectRatio;

	// --- Audio-specific ---

	/** Audio channel layout (0x01=mono, 0x03=stereo, 0x06=multi, 0x0C=combo, etc.). */
	private Integer audioChannelLayout;

	/** Sample rate (0x01=48kHz, 0x04=96kHz, 0x05=192kHz, etc.). */
	private Integer sampleRate;

	/** ISO 639-2 language code (audio and subtitle streams). */
	private String language;

	// --- Subtitle-specific ---

	/** Character encoding for text subtitles. */
	private Integer characterCode;

}
