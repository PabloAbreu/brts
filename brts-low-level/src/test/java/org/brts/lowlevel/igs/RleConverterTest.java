package org.brts.lowlevel.igs;

import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.PaletteEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RleConverter} — RLE decoding and PNG generation.
 */
class RleConverterTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // RLE decoding
    // -------------------------------------------------------------------------

    @Test
    void decodeRle_singlePixelPerByte() {
        // 2x1 image: pixel 0 = colour 1, pixel 1 = colour 2, end-of-line
        byte[] rle = { 0x01, 0x02, 0x00, 0x00 };
        IgsPalette pal = createSimplePalette();

        BufferedImage img = new RleConverter().decodeRle(rle, 2, 1, pal);

        assertThat(img.getWidth()).isEqualTo(2);
        assertThat(img.getHeight()).isEqualTo(1);
        // Colour index 1 → palette entry 1
        assertThat(img.getRGB(0, 0)).isNotEqualTo(0);
        // Colour index 2 → palette entry 2
        assertThat(img.getRGB(1, 0)).isNotEqualTo(0);
        // Different colours
        assertThat(img.getRGB(0, 0)).isNotEqualTo(img.getRGB(1, 0));
    }

    @Test
    void decodeRle_shortRunColour0() {
        // 4x1 image: 4 transparent pixels (colour 0, len=4) + eol
        // Encoding: 0x00 0b00000100 = 0x00 0x04, then 0x00 0x00 (eol)
        byte[] rle = { 0x00, 0x04, 0x00, 0x00 };
        IgsPalette pal = createSimplePalette();

        BufferedImage img = new RleConverter().decodeRle(rle, 4, 1, pal);

        assertThat(img.getWidth()).isEqualTo(4);
        // All pixels should be palette entry 0 (transparent)
        for (int x = 0; x < 4; x++) {
            assertThat(img.getRGB(x, 0) >>> 24).isEqualTo(0); // alpha=0
        }
    }

    @Test
    void decodeRle_shortRunWithColour() {
        // 3x1 image: 3 pixels of colour 5
        // Encoding: 0x00 0b10000011 0x05, then 0x00 0x00 (eol)
        // 0b10_000011 = 0x83
        byte[] rle = { 0x00, (byte) 0x83, 0x05, 0x00, 0x00 };
        IgsPalette pal = createSimplePalette();

        BufferedImage img = new RleConverter().decodeRle(rle, 3, 1, pal);

        int expected = RleConverter.ycbcrToArgb(5, 128, 128, 255);
        for (int x = 0; x < 3; x++) {
            assertThat(img.getRGB(x, 0)).isEqualTo(expected);
        }
    }

    @Test
    void decodeRle_longRunColour0() {
        // 100x1: 100 transparent pixels via 14-bit len
        // Encoding: 0x00 0b01_000000 0x64, then 0x00 0x00 (eol)
        // 0b01_000000 = 0x40
        byte[] rle = { 0x00, 0x40, 0x64, 0x00, 0x00 };
        IgsPalette pal = createSimplePalette();

        BufferedImage img = new RleConverter().decodeRle(rle, 100, 1, pal);

        assertThat(img.getWidth()).isEqualTo(100);
        for (int x = 0; x < 100; x++) {
            assertThat(img.getRGB(x, 0) >>> 24).isEqualTo(0);
        }
    }

    @Test
    void decodeRle_longRunWithColour() {
        // 64x1: 64 pixels of colour 1
        // Encoding: 0x00 0b11_000000 0x40 0x01, then eol
        // 0b11_000000 = 0xC0
        byte[] rle = { 0x00, (byte) 0xC0, 0x40, 0x01, 0x00, 0x00 };
        IgsPalette pal = createSimplePalette();

        BufferedImage img = new RleConverter().decodeRle(rle, 64, 1, pal);

        int expected = RleConverter.ycbcrToArgb(1, 128, 128, 255);
        for (int x = 0; x < 64; x++) {
            assertThat(img.getRGB(x, 0)).isEqualTo(expected);
        }
    }

    @Test
    void decodeRle_multipleLines() {
        // 2x2 image: row 0 = colour 1 (×2), row 1 = colour 2 (×2)
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        // Row 0: two single pixels + eol
        buf.write(0x01);
        buf.write(0x01);
        buf.write(0x00);
        buf.write(0x00);
        // Row 1: two single pixels + eol
        buf.write(0x02);
        buf.write(0x02);
        buf.write(0x00);
        buf.write(0x00);

        IgsPalette pal = createSimplePalette();
        BufferedImage img = new RleConverter().decodeRle(buf.toByteArray(), 2, 2, pal);

        assertThat(img.getHeight()).isEqualTo(2);
        assertThat(img.getRGB(0, 0)).isEqualTo(img.getRGB(1, 0));
        assertThat(img.getRGB(0, 1)).isEqualTo(img.getRGB(1, 1));
        assertThat(img.getRGB(0, 0)).isNotEqualTo(img.getRGB(0, 1));
    }

    // -------------------------------------------------------------------------
    // YCbCr → ARGB conversion
    // -------------------------------------------------------------------------

    @Test
    void ycbcrToArgb_white() {
        // Y=235, Cb=128, Cr=128, alpha=255 → should be near (235, 235, 235)
        int argb = RleConverter.ycbcrToArgb(235, 128, 128, 255);
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        assertThat(a).isEqualTo(255);
        assertThat(r).isEqualTo(235);
        assertThat(g).isEqualTo(235);
        assertThat(b).isEqualTo(235);
    }

    @Test
    void ycbcrToArgb_transparent() {
        int argb = RleConverter.ycbcrToArgb(0, 128, 128, 0);
        assertThat((argb >> 24) & 0xFF).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // PNG file output
    // -------------------------------------------------------------------------

    @Test
    void convertToPng_writesPngFile() throws IOException {
        // 4x2 filled with colour 1
        byte[] rle = buildRle(4, 2, 1);
        IgsPalette pal = createSimplePalette();

        Path outputPng = tempDir.resolve("test.png");
        new RleConverter().convertToPng(rle, 4, 2, pal, outputPng);

        assertThat(outputPng).exists();
        BufferedImage read = ImageIO.read(outputPng.toFile());
        assertThat(read.getWidth()).isEqualTo(4);
        assertThat(read.getHeight()).isEqualTo(2);
    }

    @Test
    void convertToPng_fromFile() throws IOException {
        byte[] rle = buildRle(8, 4, 3);
        Path rleFile = tempDir.resolve("obj.rle");
        Files.write(rleFile, rle);

        IgsPalette pal = createSimplePalette();
        Path outputPng = tempDir.resolve("obj.png");

        new RleConverter().convertToPng(rleFile, 8, 4, pal, outputPng);

        assertThat(outputPng).exists();
        BufferedImage read = ImageIO.read(outputPng.toFile());
        assertThat(read.getWidth()).isEqualTo(8);
        assertThat(read.getHeight()).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // Palette builder
    // -------------------------------------------------------------------------

    @Test
    void buildArgbPalette_mapsEntries() {
        IgsPalette pal = new IgsPalette();
        PaletteEntry e = new PaletteEntry();
        e.setEntryId(10);
        e.setY(128);
        e.setCb(128);
        e.setCr(128);
        e.setAlpha(200);
        pal.getEntries().add(e);

        int[] argb = RleConverter.buildArgbPalette(pal);
        assertThat(argb[10]).isNotEqualTo(0);
        assertThat((argb[10] >> 24) & 0xFF).isEqualTo(200);
        // Unmapped entry should be transparent
        assertThat(argb[0]).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a simple greyscale palette: index N → Y=N, Cb=128, Cr=128,
     * alpha=255 (except index 0 → transparent).
     */
    private IgsPalette createSimplePalette() {
        IgsPalette pal = new IgsPalette();
        pal.setId(0);
        pal.setVersion(0);
        for (int i = 0; i < 256; i++) {
            PaletteEntry e = new PaletteEntry();
            e.setEntryId(i);
            e.setY(i);
            e.setCr(128);
            e.setCb(128);
            e.setAlpha(i == 0 ? 0 : 255);
            pal.getEntries().add(e);
        }
        return pal;
    }

    /**
     * Builds a simple RLE stream: {@code width×height} filled with a single
     * colour using the long-run-with-colour encoding.
     */
    private byte[] buildRle(int width, int height, int colorIdx) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int row = 0; row < height; row++) {
            // Long run: 0x00 0xC0|(len>>8) (len&0xFF) colour
            buf.write(0x00);
            buf.write(0xC0 | ((width >> 8) & 0x3F));
            buf.write(width & 0xFF);
            buf.write(colorIdx);
            // End-of-line
            buf.write(0x00);
            buf.write(0x00);
        }
        return buf.toByteArray();
    }
}
