package org.brts.common.mkv;

import org.brts.common.utils.BrtsFileConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and caches the active MKV demuxer singleton.
 * <p>
 * The implementation is chosen once at first access via the {@code brts.mkv.demuxer} property read from
 * {@link BrtsFileConfig}. Accepted values:
 * <ul>
 * <li>{@code jebml} - {@link MkvDemuxer} (default)</li>
 * <li>{@code ffmpeg} - {@link FfmpegDemuxer}</li>
 * </ul>
 */
@Slf4j
public final class MkvDemuxerFactory {

	public static final String DEMUXER_PROPERTY = "brts.mkv.demuxer";

	public static final String DEMUXER_JEBML = "jebml";

	public static final String DEMUXER_FFMPEG = "ffmpeg";

	private static volatile MkvDemuxer instance;

	private MkvDemuxerFactory() {
	}

	/** Returns the active {@link MkvDemuxer} singleton (created on first call). */
	public static MkvDemuxer get() {
		if (instance == null) {
			synchronized (MkvDemuxerFactory.class) {
				if (instance == null) {
					instance = create();
				}
			}
		}
		return instance;
	}

	private static MkvDemuxer create() {
		String value = BrtsFileConfig.getInstance().getProperty(DEMUXER_PROPERTY);
		// JEBML only when requested, otherwise FFmpeg is preferred if available
		if (DEMUXER_JEBML.equalsIgnoreCase(value)) {
			log.info("MKV demuxer: JEBML");
			return new MkvDemuxer();
		}
		if (value != null && !value.isBlank() && !DEMUXER_FFMPEG.equalsIgnoreCase(value)) {
			log.warn("Unknown MKV demuxer '{}', falling back to FFmpeg", value);
		}
		log.info("MKV demuxer: FFmpeg (default)");
		return new FfmpegDemuxer();
	}
}
