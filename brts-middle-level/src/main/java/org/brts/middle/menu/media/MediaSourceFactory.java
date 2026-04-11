package org.brts.middle.menu.media;

import org.brts.middle.menu.descriptor.BackgroundMediaDescriptor;
import org.brts.middle.menu.descriptor.MkvBackgroundMedia;

import java.nio.file.Path;

/**
 * Factory that creates the appropriate {@link MediaSource} implementation from a
 * {@link BackgroundMediaDescriptor}.
 */
public final class MediaSourceFactory {

	private MediaSourceFactory() {
	}

	/**
	 * Creates a {@link MediaSource} from the given descriptor.
	 * @param descriptor the background media descriptor
	 * @return the appropriate media source implementation
	 * @throws IllegalArgumentException if the descriptor type is unsupported
	 */
	public static MediaSource create(BackgroundMediaDescriptor descriptor) {
		if (descriptor instanceof MkvBackgroundMedia mkv) {
			return new MkvMediaSource(Path.of(mkv.getFile()), mkv.getVideoTrackNumber(), mkv.getAudioTrackNumber());
		}
		throw new IllegalArgumentException(
				"Unsupported background media type: " + descriptor.getClass().getSimpleName());
	}

}
