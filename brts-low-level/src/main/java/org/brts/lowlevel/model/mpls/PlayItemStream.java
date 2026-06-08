package org.brts.lowlevel.model.mpls;

import lombok.Getter;
import lombok.Setter;

import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.model.StreamCodingType;

/**
 * Stream entry in a PlayItem STN (Stream Number Table). Specifies which PID to present and carries the same stream
 * attributes as the corresponding CLPI ClipStream entry.
 */
@Getter
@Setter
public class PlayItemStream implements IStreamInfo {
	public static final int STREAM_TYPE_IN_MUX = 0x01;
	public static final int STREAM_TYPE_OUT_OF_MUX = 0x02;
	// so says chatgpt
	public static final int STREAM_TYPE_REPEATABLE_OUT_OF_MUX = 0x03;
	private int pid;

	private int streamType; // 0x01=in-mux, 0x02=out-of-mux, etc.

	/** Sub-path index (stream_type 2, 3, 4). */
	private int subpathId;

	/** Sub-clip index within the sub-path (stream_type 2 only). */
	private int subclipId;

	private StreamCodingType codingType;

	/** ISO 639-2 language code (audio / subtitle streams). */
	private String language;

	// Attributes mirror ClipStream — duplicated here as the MPLS carries its own
	// copy.

	private Integer videoFormat;// video format code

	private Integer frameRate;// frame rate code

	private Integer audioChannelLayout;

	private Integer sampleRate;

	public Integer sampleRateKhz() {
		return sampleRate != null ? switch (sampleRate) {
		case 0x01 -> 48;// this one seems good
		default -> null;
		} : null;
	}

}
