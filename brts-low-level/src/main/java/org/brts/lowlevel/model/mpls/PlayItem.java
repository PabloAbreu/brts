package org.brts.lowlevel.model.mpls;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A PlayItem references one M2TS clip and defines the in/out point
 * within that clip expressed as 90 kHz PTS values.
 */
@Getter
@Setter
public class PlayItem {

    /** Clip name without extension (5-digit, e.g. "00001"). */
    private String clipName;

    /** Clip codec identifier (usually "M2TS"). */
    private int connectionCondition = 1;

    /** Is seamless angle change allowed? (always false for single-angle). */
    @JsonProperty("isMultiAngle")
    private boolean isMultiAngle = false;

    /** Presentation start time in 90 kHz ticks (in/out within the clip). */
    private long inTimeTicks;

    /** Presentation end time in 90 kHz ticks. */
    private long outTimeTicks;

    /** Stream-table — PIDs and attributes of streams to present from this clip. */
    private List<PlayItemStream> streams;
}

