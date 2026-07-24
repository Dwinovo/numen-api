package com.dwinovo.numen.client.vision;

/**
 * Image budget for one request-local visual observation.
 *
 * <p>Hybrid mode already carries structured world context, so a smaller low-detail frame is enough
 * for orientation and costs substantially fewer image tokens. Pure visual mode keeps the larger,
 * high-detail frame because pixels are its primary ambient observation.
 */
public record VisionCaptureProfile(int maxWidth, int maxHeight, float jpegQuality, String detail) {

    private static final VisionCaptureProfile HYBRID =
            new VisionCaptureProfile(960, 540, 0.68F, "low");
    private static final VisionCaptureProfile VISUAL =
            new VisionCaptureProfile(1280, 720, 0.80F, "high");

    public VisionCaptureProfile {
        maxWidth = Math.max(1, maxWidth);
        maxHeight = Math.max(1, maxHeight);
        jpegQuality = Math.max(0.1F, Math.min(1.0F, jpegQuality));
        detail = "high".equalsIgnoreCase(detail) ? "high" : "low";
    }

    public static VisionCaptureProfile forMode(ObservationMode mode) {
        return mode == ObservationMode.VISUAL ? VISUAL : HYBRID;
    }

    /** Aspect-preserving dimensions bounded by this profile. Never upscales a framebuffer. */
    public int[] dimensionsFor(int sourceWidth, int sourceHeight) {
        int safeWidth = Math.max(1, sourceWidth);
        int safeHeight = Math.max(1, sourceHeight);
        double scale = Math.min(1.0D, Math.min(
                maxWidth / (double) safeWidth, maxHeight / (double) safeHeight));
        return new int[] {
                Math.max(1, (int) Math.round(safeWidth * scale)),
                Math.max(1, (int) Math.round(safeHeight * scale))
        };
    }
}
