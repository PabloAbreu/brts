package org.brts.middle.pid;

import org.brts.common.model.StreamCodingType;

/**
 * Automatic PID allocator for Blu-ray elementary streams.
 * <p>
 * Follows the Blu-ray default PID allocation convention: <pre>
 *   PMT              0x0100 (256)
 *   Video            0x1011 (4113)
 *   Primary audio    0x1100–0x110F
 *   Secondary audio  0x1A00–0x1A0F
 *   PG subtitles     0x1200–0x120F
 *   IG (menu)        0x1400–0x140F
 *   Text subtitles   0x1800–0x180F
 * </pre> Each call to {@code next*()} returns the next available PID in the respective
 * range.
 */
public class PidAllocator {

	private int nextVideo = 0x1011;

	private int nextAudio = 0x1100;

	private int nextSecAudio = 0x1A00;

	private int nextPg = 0x1200;

	private int nextIg = 0x1400;

	private int nextTextSub = 0x1800;

	public int allocate(StreamCodingType type) {
		return switch (type) {
			case H264_AVC, H265_HEVC, MPEG2_VIDEO, VC1 -> nextVideo++;
			case DOLBY_AC3, DOLBY_AC3_PLUS, DOLBY_TRUEHD, DTS, DTS_HD, DTS_HD_MASTER_AUDIO, LPCM -> nextAudio++;
			case PRESENTATION_GRAPHICS -> nextPg++;
			case INTERACTIVE_GRAPHICS -> nextIg++;
			case TEXT_SUBTITLE -> nextTextSub++;
		};
	}

	/** Allocates a secondary audio PID. */
	public int allocateSecondaryAudio() {
		return nextSecAudio++;
	}

	public void reset() {
		nextVideo = 0x1011;
		nextAudio = 0x1100;
		nextSecAudio = 0x1A00;
		nextPg = 0x1200;
		nextIg = 0x1400;
		nextTextSub = 0x1800;
	}

}
