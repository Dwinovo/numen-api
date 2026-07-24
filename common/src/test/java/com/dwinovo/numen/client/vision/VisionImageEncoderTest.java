package com.dwinovo.numen.client.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionImageEncoderTest {

    @Test
    void downscalesAndProducesDecodableJpeg() throws Exception {
        BufferedImage source = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(new Color(20, 80, 140));
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);

        var observation = VisionImageEncoder.encode(png.toByteArray(), 800, 600, 0.7F);
        assertEquals("image/jpeg", observation.mimeType());
        assertEquals(800, observation.width());
        assertEquals(500, observation.height());
        byte[] jpeg = Base64.getDecoder().decode(observation.base64());
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(800, decoded.getWidth());
        assertEquals(500, decoded.getHeight());
        assertTrue(jpeg.length < png.size());
    }
}
