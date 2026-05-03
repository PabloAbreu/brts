package org.brts.lowlevel.titlemenu.layout;

import java.io.IOException;
import java.nio.file.Path;

import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;

/**
 * Strategy interface for title menu layout implementations.
 * <p>
 * Each implementation arranges title buttons on screen and produces rendered images for the three IGS states
 * (normal/selected/activated). Optionally, a layout may also produce an
 * {@link org.brts.common.utils.composition.ImagesComposition} descriptor for background video generation (e.g. when
 * animated thumbnails need to be composited into the background).
 */
public interface TitleMenuLayout {

	/**
	 * Computes the layout and renders button images.
	 *
	 * @param descriptor the title menu descriptor with titles, layout config, and screen dimensions
	 * @param baseDir    base directory for resolving relative paths in the descriptor
	 * @return layout result with positioned buttons and optional background composition
	 * @throws IOException on I/O errors during media access (e.g. thumbnail extraction)
	 */
	LayoutResult layout(TitleMenuDescriptor descriptor, Path baseDir) throws IOException;

}
