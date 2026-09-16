package org.brts.lowlevel.igs;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.junit.jupiter.api.Test;

class IgsMenuAssemblerTest {

	@Test
	void buildObjectsFromImages_fragmentsLargeRleWithoutChangingLogicalObjectId() {
		BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, (x & 1) == 0 ? 0xFFFFFFFF : 0xFF000000);
			}
		}
		IgsPalette palette = PaletteBuilder.buildFromImages(0, image);

		List<IgsObject> fragments = IgsMenuAssembler.buildObjectsFromImages(List.of(image), palette);

		assertThat(fragments).hasSizeGreaterThan(1).allSatisfy(fragment -> {
			assertThat(fragment.getId()).isZero();
			assertThat(IgsPgsCodec.encodeObject(fragment).length).isLessThanOrEqualTo(IgsPgsCodec.MAX_ODS_DATA_LENGTH);
		});
		assertThat(fragments.get(0).getSequenceDescriptor().isFirstInSequence()).isTrue();
		assertThat(fragments.get(0).getSequenceDescriptor().isLastInSequence()).isFalse();
		assertThat(fragments.get(fragments.size() - 1).getSequenceDescriptor().isFirstInSequence()).isFalse();
		assertThat(fragments.get(fragments.size() - 1).getSequenceDescriptor().isLastInSequence()).isTrue();
	}

}