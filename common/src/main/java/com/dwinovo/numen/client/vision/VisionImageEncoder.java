package com.dwinovo.numen.client.vision;

import com.dwinovo.numen.agent.llm.VisualObservation;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/** Downsamples a raw Minecraft framebuffer PNG into a token/bandwidth-conscious JPEG. */
public final class VisionImageEncoder {

    /** 1280×720 retains block/UI detail while avoiding multi-megabyte request bodies. */
    static final int DEFAULT_MAX_WIDTH = 1280;
    static final int DEFAULT_MAX_HEIGHT = 720;
    static final float DEFAULT_JPEG_QUALITY = 0.78F;

    private VisionImageEncoder() {}

    public static VisualObservation encode(byte[] png) throws IOException {
        return encode(png, DEFAULT_MAX_WIDTH, DEFAULT_MAX_HEIGHT, DEFAULT_JPEG_QUALITY);
    }

    static VisualObservation encode(byte[] png, int maxWidth, int maxHeight, float quality)
            throws IOException {
        if (png == null || png.length == 0) throw new IOException("empty framebuffer image");
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
        if (source == null) throw new IOException("framebuffer PNG could not be decoded");

        double scale = Math.min(1.0D, Math.min(
                Math.max(1, maxWidth) / (double) source.getWidth(),
                Math.max(1, maxHeight) / (double) source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // JPEG has no alpha. Drawing onto TYPE_INT_RGB also avoids provider-specific failures on
        // four-channel JPEG encoders and gives deterministic black-free world frames.
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    scale < 1.0D ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16_384, width * height / 4));
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("no JPEG ImageIO writer available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.max(0.1F, Math.min(1.0F, quality)));
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }

        return new VisualObservation("image/jpeg",
                Base64.getEncoder().encodeToString(out.toByteArray()), width, height, "high");
    }
}
