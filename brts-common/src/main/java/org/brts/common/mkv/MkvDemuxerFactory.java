package org.brts.common.mkv;

import org.brts.common.utils.BrtsFileConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and caches the active MKV demuxer singleton.
 * <p>
 * The implementation is chosen once at first access via the {@code brts.mkv.demuxer} property read from
 * {@link BrtsFileConfig}. Accepted values:
 * <ul>
 * <li>{@code jebml} - {@link MkvDemuxer}</li>
 * <li>{@code ffmpeg} - {@link FfmpegDemuxer} (default)</li>
 * <li>{@code tsmuxer} - {@link TsMuxerDemuxer}</li>
 * </ul>
 */
@Slf4j
public final class MkvDemuxerFactory {

	public static final String DEMUXER_PROPERTY = "brts.mkv.demuxer";

	public static final String DEMUXER_JEBML = "jebml";

	public static final String DEMUXER_FFMPEG = "ffmpeg";

	public static final String DEMUXER_TSMUXER = "tsmuxer";

	private static volatile EsDemuxer instance;

	private MkvDemuxerFactory() {
	}

	/** Returns the active {@link EsDemuxer} singleton (created on first call). */
	public static EsDemuxer get() {
		if (instance == null) {
			synchronized (MkvDemuxerFactory.class) {
				if (instance == null) {
					instance = create();
				}
			}
		}
		return instance;
	}

	private static EsDemuxer create() {
		String value = BrtsFileConfig.getInstance().getProperty(DEMUXER_PROPERTY);
		// JEBML only when requested, otherwise FFmpeg is preferred if available
		if (DEMUXER_JEBML.equalsIgnoreCase(value)) {
			log.info("MKV demuxer: JEBML");
			return new MkvDemuxer();
		}
		if (DEMUXER_TSMUXER.equalsIgnoreCase(value)) {
			log.info("MKV demuxer: tsMuxeR");
			return new TsMuxerDemuxer();
		}
		if (value != null && !value.isBlank() && !DEMUXER_FFMPEG.equalsIgnoreCase(value)) {
			log.warn("Unknown MKV demuxer '{}', falling back to FFmpeg", value);
		}
		log.info("MKV demuxer: FFmpeg (default)");
		return new FfmpegDemuxer();
	}
}
