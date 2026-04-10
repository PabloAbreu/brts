package org.brts.middle.menu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Background media descriptor for MKV files.
 * <p>
 * The MKV is used to extract the video and audio elementary streams that form
 * the background of the setup menu.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkvBackgroundMedia extends BackgroundMediaDescriptor {

    /** Path to the MKV file on disk. */
    private String file;

    /**
     * Optional: MKV track number for the video stream to extract.
     * If {@code null}, the first video track is used.
     */
    private Integer videoTrackNumber;

    /**
     * Optional: MKV track number for the audio stream to extract.
     * If {@code null}, the first audio track is used.
     */
    private Integer audioTrackNumber;
}
