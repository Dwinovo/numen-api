package com.dwinovo.numen.client.vision;

import com.dwinovo.numen.agent.llm.VisualObservation;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/** Encodes an already sampled Minecraft framebuffer into a bandwidth-conscious JPEG. */
public final class VisionImageEncoder {

    private VisionImageEncoder() {}

    /**
     * Encode ARGB pixels copied from a bounded {@code NativeImage}. The framebuffer is sampled to
     * its target dimensions before this asynchronous stage, avoiding the former render-thread PNG
     * compression followed by an ImageIO PNG decode.
     */
    public static VisualObservation encode(int[] argb, int width, int height,
                                           VisionCaptureProfile profile) throws IOException {
        if (width < 1 || height < 1 || argb == null || argb.length != width * height) {
            throw new IOException("invalid framebuffer pixels");
        }
        VisionCaptureProfile safeProfile = profile == null
                ? VisionCaptureProfile.forMode(ObservationMode.HYBRID) : profile;
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, width, height, argb, 0, width);

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16_384, width * height / 4));
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("no JPEG ImageIO writer available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(safeProfile.jpegQuality());
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }

        return new VisualObservation("image/jpeg",
                Base64.getEncoder().encodeToString(out.toByteArray()), width, height,
                safeProfile.detail());
    }
}
