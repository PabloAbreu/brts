package org.brts.lowlevel.thumbnail;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON descriptor listing the thumbnails extracted from the chapters of a source media file.
 * <p>
 * Image paths are relative to the directory holding the descriptor.
 */
@Getter
@Setter
public class ChapterThumbnailsDescriptor {

	/** Source media file the thumbnails were extracted from. */
	private String sourceMedia;

	/** Width in pixels of every generated thumbnail. */
	private int thumbnailWidth;

	/** Height in pixels of every generated thumbnail. */
	private int thumbnailHeight;

	private List<ChapterThumbnail> chapters = new ArrayList<>();

	@Getter
	@Setter
	public static class ChapterThumbnail {

		/** 1-based chapter index. */
		private int index;

		/** Chapter display name, or a generated {@code Chapter NN} label when the source has no title. */
		private String title;

		/** Chapter start time in seconds. */
		private double startTimeSeconds;

		/** Chapter start time in 90 kHz ticks. */
		private long startTimeTicks;

		/** Descriptor-relative path of the PNG thumbnail. */
		private String imageFile;

	}

}
