package org.brts.lowlevel.model.clpi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.brts.common.model.Timestamp;

import java.util.List;

/**
 * Model for a CLPI (Clip Information) file — {@code STREAM/CLIPINF/XXXXX.clpi}.
 * <p>
 * The CLPI file accompanies each M2TS stream and carries:
 * <ul>
 * <li>Clip stream-type and codec metadata</li>
 * <li>PID map for all elementary streams in the clip</li>
 * <li>An Entry Point Map (EP_map) used for random access / chapter seeking</li>
 * <li>Sequence information (start/end PTS, byte offsets)</li>
 * </ul>
 */
@Getter
@Setter
public class ClipInfo {

	public static final int APPLICATION_TYPE_MOVIE = 1;
	public static final int APPLICATION_TYPE_TIME_BASED_SLIDESHOW = 2;
	public static final int APPLICATION_TYPE_BROWSABLE_SLIDESHOW_MAIN = 3;
	public static final int APPLICATION_TYPE_BROWSABLE_SLIDESHOW_SUBPATH = 4;
	public static final int APPLICATION_TYPE_INTERACTIVE_GRAPHICS = 5;
	public static final int APPLICATION_TYPE_TEXT_SUBTITLE = 6;

	/**
	 * Base name of the associated M2TS file, without extension (5 digits, e.g. "00001").
	 */
	private String clipName;

	/** Blu-ray clip stream type (always 1 for AV clip in practice). */
	private int clipStreamType = 1;

	/**
	 * Application type (1 = Movie, 2 = Time-based slideshow, 3 = Browsable slideshow main, 4 = Browsable slideshow
	 * subpath, 5 = Interactive graphics, 6 = Text subtitle).
	 */
	private int applicationType = 1;

	/** Is this clip encoded as a TS recording? */
	@JsonProperty("isAtcDelta")
	private boolean isAtcDelta = false;

	/** TS recording rate in bytes per second. */
	private long tsRecordingRate;

	/** Total number of 192-byte source packets in the clip. */
	private long numSourcePackets;

	/** Total length of the clip in 90 kHz ticks. */
	private Timestamp duration;

	/** TS-recording start PTS. */
	private Timestamp tsRecordingStartPts;

	/** TS-recording end PTS. */
	private Timestamp tsRecordingEndPts;

	/** All program tracks (video, audio, PG, IG). */
	private List<ClipStream> streams;

	/** Entry-point map — used for chapter / seek access. */
	private EpMap epMap;

}
