package com.vastra.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies LabelPrintUtil's monochrome bit-packing (the exact bytes sent to
 * the printer as a TSPL BITMAP payload) against a known pixel pattern. Width
 * 13 is deliberately not a multiple of 8, to exercise right-edge padding -
 * a subtle place for a raw-printing bitmap encoder to silently corrupt the
 * image if it isn't handled correctly.
 */
class LabelPrintUtilBitmapTest {

    @Test
    void packMonochromeBitmap_matchesSourcePixels_includingRightEdgePadding() throws Exception {
        int width = 13, height = 5;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, 0xFFFFFFFF); // white
            }
        }
        // Black cross through the middle row/column.
        for (int x = 0; x < width; x++) img.setRGB(x, 2, 0xFF000000);
        for (int y = 0; y < height; y++) img.setRGB(6, y, 0xFF000000);
        // Black pixel in the very last column, to check the true final column is captured.
        img.setRGB(width - 1, 0, 0xFF000000);

        Method pack = LabelPrintUtil.class.getDeclaredMethod("packMonochromeBitmap", BufferedImage.class, int.class);
        pack.setAccessible(true);

        int widthBytes = (width + 7) / 8; // = 2
        byte[] data = (byte[]) pack.invoke(null, img, widthBytes);

        assertEquals(widthBytes * height, data.length);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int byteIndex = y * widthBytes + (x / 8);
                int bitIndex = 7 - (x % 8);
                boolean bitSet = (data[byteIndex] & (1 << bitIndex)) != 0;
                boolean expectedInk = (img.getRGB(x, y) & 0xFFFFFF) == 0x000000;
                assertEquals(expectedInk, bitSet, "mismatch at x=" + x + ",y=" + y);
            }
        }

        // Bits for x=13,14,15 (beyond the real 13px width but inside the
        // padded byte) must never be set, or content would silently shift.
        int paddingMask = 0b0000_0111;
        for (int y = 0; y < height; y++) {
            int paddingByte = data[y * widthBytes + 1];
            assertEquals(0, paddingByte & paddingMask, "padding bits must be 0 for row " + y);
        }
    }
}
