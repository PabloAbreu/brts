package org.brts.common.m2ts;

import java.io.IOException;
import java.nio.file.Path;

import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class M2tsClipWriterImpl implements M2tsClipWriter {

	@Override
	public void write(M2tsDescriptor descriptor, Path m2tsPath, Path clpiPath) throws IOException {
		// TODO Auto-generated method stub

		// --- Write M2TS ---
		M2tsWriter writer = new M2tsWriter();
		writer.write(descriptor, m2tsPath);
		log.info("M2TS written → " + m2tsPath);

		// --- Build and write matching CLPI ---
		int applicationType = ClipInfo.APPLICATION_TYPE_MOVIE;
		// if only one stream and it's a IGS, set application type to interactive graphics
		if (descriptor.getStreams().size() == 1 && descriptor.getStreams().get(0)
				.getStreamTypeByte() == StreamCodingType.INTERACTIVE_GRAPHICS.getCodingTypeByte()) {
			applicationType = ClipInfo.APPLICATION_TYPE_INTERACTIVE_GRAPHICS;
		}
		ClipInfo clipInfo = writer.buildClipInfo(descriptor, clpiPath.getFileName().toString().split("\\.")[0],
				applicationType);
		new ClipInfoWriter().write(clipInfo, clpiPath);
		log.info("CLPI written → " + clpiPath);
	}

}
