package org.brts.lowlevel.model.mpls;

import lombok.Getter;
import lombok.Setter;

import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.model.StreamCodingType;

/**
 * Stream entry in a PlayItem STN (Stream Number Table).
 * Specifies which PID to present and carries the same stream attributes
 * as the corresponding CLPI ClipStream entry.
 */
@Getter
@Setter
public class PlayItemStream implements IStreamInfo {

    private int pid;

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
