package org.brts.lowlevel.m2ts;

import java.io.IOException;
import java.nio.file.Path;

import org.brts.common.m2ts.model.M2tsDescriptor;

public interface M2tsClipWriter {

	void write(M2tsDescriptor descriptor, Path m2tsPath, Path clipPath) throws IOException;

}
