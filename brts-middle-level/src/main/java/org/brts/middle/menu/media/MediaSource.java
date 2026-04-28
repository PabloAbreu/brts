package org.brts.middle.menu.media;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for extracting elementary streams (video, audio) from a container media file to be used as background
 * content in a setup menu.
 * <p>
 * Implementations handle specific container formats (MKV, MP4, etc.). The extracted elementary streams are written as
 * raw files suitable for feeding into {@link org.brts.common.m2ts.M2tsWriter}.
 */
public interface MediaSource {

	/**
	 * Result of extracting background media elementary streams.
	 *
	 * @param videoEsFile         path to the extracted raw video ES file
	 * @param audioEsFile         path to the extracted raw audio ES file (may be null if no audio)
	 * @param videoStreamTypeByte ISO 13818-1 stream_type for the video (e.g. 0x1B for H.264)
	 * @param audioStreamTypeByte ISO 13818-1 stream_type for the audio (e.g. 0x81 for AC3)
	 * @param frameRateFps        video frame rate (null if unknown)
	 * @param audioLanguage       ISO 639-2 language code for the audio track (may be null)
	 * @param audioSampleRateHz   audio sample rate in Hz (may be null)
	 * @param audioBitrateKbps    audio bitrate in kbps (may be null)
	 * @param audioChannels       number of audio channels (may be null)
	 */
	record ExtractionResult(Path videoEsFile, Path audioEsFile, int videoStreamTypeByte, int audioStreamTypeByte,
			Double frameRateFps, String audioLanguage, Integer audioSampleRateHz, Integer audioBitrateKbps,
			Integer audioChannels) {
	}

	/**
	 * Extracts the video and audio elementary streams from the source media into the given working directory.
	 *
	 * @param workDir directory where temporary ES files will be written
	 * @return the extraction result with paths and stream metadata
	 * @throws IOException on I/O or extraction error
	 */
	ExtractionResult extract(Path workDir) throws IOException;

}
