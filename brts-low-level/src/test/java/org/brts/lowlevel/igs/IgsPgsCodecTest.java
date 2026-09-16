package org.brts.lowlevel.igs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.SequenceDescriptor;
import org.junit.jupiter.api.Test;

class IgsPgsCodecTest {

	private static final int FIRST_FRAGMENT_RLE_LENGTH = IgsPgsCodec.MAX_ODS_DATA_LENGTH - 11;

	private static final int CONTINUATION_FRAGMENT_RLE_LENGTH = IgsPgsCodec.MAX_ODS_DATA_LENGTH - 4;

	@Test
	void fragmentObject_keepsExactBoundaryInSingleSegment() {
		IgsObject object = objectWithRleLength(FIRST_FRAGMENT_RLE_LENGTH);

		List<IgsObject> fragments = IgsPgsCodec.fragmentObject(object);

		assertThat(fragments).singleElement().satisfies(fragment -> {
			assertThat(fragment.getSequenceDescriptor().isFirstInSequence()).isTrue();
			assertThat(fragment.getSequenceDescriptor().isLastInSequence()).isTrue();
			assertThat(IgsPgsCodec.encodeObject(fragment)).hasSize(IgsPgsCodec.MAX_ODS_DATA_LENGTH);
		});
	}

	@Test
	void fragmentObject_splitsOversizedObjectAndPreservesLogicalFields() {
		IgsObject object = objectWithRleLength(FIRST_FRAGMENT_RLE_LENGTH + 1);

		List<IgsObject> fragments = IgsPgsCodec.fragmentObject(object);

		assertThat(fragments).hasSize(2).allSatisfy(fragment -> {
			assertThat(fragment.getId()).isEqualTo(object.getId());
			assertThat(fragment.getVersion()).isEqualTo(object.getVersion());
			assertThat(fragment.getPts()).isEqualTo(object.getPts());
			assertThat(IgsPgsCodec.encodeObject(fragment).length).isLessThanOrEqualTo(IgsPgsCodec.MAX_ODS_DATA_LENGTH);
		});
		assertThat(fragments.get(0).getSequenceDescriptor().isFirstInSequence()).isTrue();
		assertThat(fragments.get(0).getSequenceDescriptor().isLastInSequence()).isFalse();
		assertThat(fragments.get(0).getDataLength()).isEqualTo(object.getRleData().length + 4);
		assertThat(fragments.get(0).getWidth()).isEqualTo(object.getWidth());
		assertThat(fragments.get(1).getSequenceDescriptor().isFirstInSequence()).isFalse();
		assertThat(fragments.get(1).getSequenceDescriptor().isLastInSequence()).isTrue();
		assertThat(fragments.get(1).getDataLength()).isZero();
		assertThat(fragments.get(1).getWidth()).isZero();
	}

	@Test
	void fragmentAndReassembleObject_isLosslessAcrossThreeSegments() {
		IgsObject original = objectWithRleLength(FIRST_FRAGMENT_RLE_LENGTH + CONTINUATION_FRAGMENT_RLE_LENGTH + 1);

		List<IgsObject> fragments = IgsPgsCodec.fragmentObject(original);
		List<IgsObject> reassembled = IgsPgsCodec.reassembleObjects(fragments);

		assertThat(fragments).hasSize(3);
		assertThat(fragments).extracting(fragment -> fragment.getSequenceDescriptor().isFirstInSequence())
				.containsExactly(true, false, false);
		assertThat(fragments).extracting(fragment -> fragment.getSequenceDescriptor().isLastInSequence())
				.containsExactly(false, false, true);
		assertThat(reassembled).singleElement().satisfies(object -> {
			assertThat(object.getId()).isEqualTo(original.getId());
			assertThat(object.getVersion()).isEqualTo(original.getVersion());
			assertThat(object.getPts()).isEqualTo(original.getPts());
			assertThat(object.getWidth()).isEqualTo(original.getWidth());
			assertThat(object.getHeight()).isEqualTo(original.getHeight());
			assertThat(object.getRleData()).isEqualTo(original.getRleData());
			assertThat(object.getSequenceDescriptor().isFirstInSequence()).isTrue();
			assertThat(object.getSequenceDescriptor().isLastInSequence()).isTrue();
		});
	}

	@Test
	void reassembleObjects_rejectsContinuationWithoutFirstFragment() {
		IgsObject fragment = objectWithRleLength(1);
		fragment.setSequenceDescriptor(sequence(false, true));

		assertThatThrownBy(() -> IgsPgsCodec.reassembleObjects(List.of(fragment)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Object 42 continuation has no first fragment");
	}

	@Test
	void reassembleObjects_rejectsIncompleteSequence() {
		IgsObject fragment = objectWithRleLength(1);
		fragment.setSequenceDescriptor(sequence(true, false));

		assertThatThrownBy(() -> IgsPgsCodec.reassembleObjects(List.of(fragment)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Object 42 sequence is missing its last fragment");
	}

	private static IgsObject objectWithRleLength(int length) {
		byte[] rleData = new byte[length];
		for (int i = 0; i < rleData.length; i++) {
			rleData[i] = (byte) i;
		}

		IgsObject object = new IgsObject();
		object.setPts(90_000);
		object.setId(42);
		object.setVersion(3);
		object.setSequenceDescriptor(sequence(true, true));
		object.setDataLength(rleData.length + 4);
		object.setWidth(1920);
		object.setHeight(1080);
		object.setRleData(rleData);
		return object;
	}

	private static SequenceDescriptor sequence(boolean first, boolean last) {
		SequenceDescriptor sequence = new SequenceDescriptor();
		sequence.setFirstInSequence(first);
		sequence.setLastInSequence(last);
		return sequence;
	}

}