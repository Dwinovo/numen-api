package com.dwinovo.numen.client.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionImageEncoderTest {

    @Test
    void producesDecodableJpegFromRawFramebufferPixels() throws Exception {
        int width = 800;
        int height = 500;
        int[] argb = new int[width * height];
        Arrays.fill(argb, 0xFF14508C);
        VisionCaptureProfile profile = new VisionCaptureProfile(800, 600, 0.7F, "low");

        var observation = VisionImageEncoder.encode(argb, width, height, profile);
        assertEquals("image/jpeg", observation.mimeType());
        assertEquals(width, observation.width());
        assertEquals(height, observation.height());
        assertEquals("low", observation.detail());
        byte[] jpeg = Base64.getDecoder().decode(observation.base64());
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        assertTrue(jpeg.length < argb.length * Integer.BYTES);
    }
}
