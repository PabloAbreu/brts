package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SVGImageGenerator implements SyntheticImageGenerator {
    private final String svgContent;

    @Override
    public BufferedImage generate(int frameNumber) {
        // TODO implement SVG image generation from synthetic content
        throw new UnsupportedOperationException("SVG image generation from synthetic content not implemented yet");
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

}
