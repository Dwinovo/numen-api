package com.dwinovo.numen.client.vision;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.agent.llm.VisualObservation;
import com.dwinovo.numen.client.agent.ClientNumenLookup;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Captures a clean first-person frame from a companion body's camera.
 *
 * <p>A framebuffer read must happen on the render thread after the level has been drawn. Agent
 * turns originate on the client thread, so {@link #request(UUID, ObservationMode)} queues a capture and
 * {@code MixinGameRenderer} brackets the next world render with {@link #beginFrame(Minecraft)} and
 * {@link #captureFrame(Minecraft)}. The owner's camera settings are restored immediately after the
 * framebuffer read. Native downsampling avoids PNG compression on the render thread; only JPEG
 * encoding runs on Minecraft's I/O pool.
 *
 * <p>The screen/HUD are not part of the captured pixels: the mixin samples immediately after
 * {@code renderLevel}, before GUI composition, and this class temporarily enables hide-GUI to keep
 * the owner's hand/hotbar out of the companion's observation. The normal rendered frame may use the
 * companion camera for one refresh, but the player's camera is restored before GUI rendering.
 */
public final class VisionObservationCapture {

    private static final int MAX_QUEUED = 8;
    private static final long CAPTURE_TIMEOUT_SECONDS = 3L;
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();

    private record Request(UUID entityUuid, VisionCaptureProfile profile,
                           CompletableFuture<VisualObservation> future) {}

    private record Active(Request request, Entity previousCamera, CameraType previousType,
                          boolean previousHideGui) {}

    /** Only touched on the render/client thread after it is assigned by {@link #beginFrame}. */
    private static Active active;

    private VisionObservationCapture() {}

    /** Queue one fresh frame. A missing/unloaded body or a render timeout resolves to {@code null}. */
    public static CompletableFuture<VisualObservation> request(UUID entityUuid, ObservationMode mode) {
        if (entityUuid == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<VisualObservation> future = new CompletableFuture<>();
        VisionCaptureProfile profile = VisionCaptureProfile.forMode(mode);
        synchronized (QUEUE) {
            while (QUEUE.size() >= MAX_QUEUED) {
                Request dropped = QUEUE.pollFirst();
                if (dropped != null) dropped.future().complete(null);
            }
            QUEUE.addLast(new Request(entityUuid, profile, future));
        }
        // A minimized/paused client may not render another world frame. Never hold the agent loop
        // hostage indefinitely; a text/tool-only request is a valid fallback.
        future.completeOnTimeout(null, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return future;
    }

    /** Called at GameRenderer.render HEAD. Returns true when this frame was switched to a Numen POV. */
    public static boolean beginFrame(Minecraft minecraft) {
        if (active != null || minecraft == null || minecraft.level == null) return false;
        Request request;
        while (true) {
            synchronized (QUEUE) {
                request = QUEUE.pollFirst();
            }
            if (request == null) return false;
            if (!request.future().isDone()) break;
        }

        Entity body = ClientNumenLookup.resolve(request.entityUuid());
        if (body == null) {
            request.future().complete(null);
            return false;
        }

        active = new Active(request, minecraft.getCameraEntity(),
                minecraft.options.getCameraType(), minecraft.options.hideGui);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.options.hideGui = true;
        minecraft.setCameraEntity(body);
        return true;
    }

    /** Called immediately after renderLevel and before any GUI is composited. */
    public static void captureFrame(Minecraft minecraft) {
        Active frame = active;
        if (frame == null) return;

        int[] argb = null;
        int width = 0;
        int height = 0;
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int[] dimensions = frame.request().profile().dimensionsFor(image.getWidth(), image.getHeight());
            width = dimensions[0];
            height = dimensions[1];
            if (width == image.getWidth() && height == image.getHeight()) {
                argb = copyArgb(image);
            } else {
                try (NativeImage sampled = new NativeImage(width, height, false)) {
                    image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), sampled);
                    argb = copyArgb(sampled);
                }
            }
        } catch (Exception ex) {
            Constants.LOG.warn("[numen-vision] framebuffer capture failed: {}", ex.toString());
        } finally {
            restore(minecraft, frame);
            active = null;
        }

        if (argb == null) {
            frame.request().future().complete(null);
            return;
        }
        int[] encodedSource = argb;
        int encodedWidth = width;
        int encodedHeight = height;
        CompletableFuture.supplyAsync(() -> {
            try {
                return VisionImageEncoder.encode(encodedSource, encodedWidth, encodedHeight,
                        frame.request().profile());
            } catch (Exception ex) {
                Constants.LOG.warn("[numen-vision] frame encoding failed: {}", ex.toString());
                return null;
            }
        }, Util.ioPool()).thenAccept(frame.request().future()::complete);
    }

    /** RETURN safety net for frames where renderLevel was skipped and captureFrame was never reached. */
    public static void endFrameWithoutCapture(Minecraft minecraft) {
        Active frame = active;
        if (frame == null) return;
        restore(minecraft, frame);
        active = null;
        frame.request().future().complete(null);
    }

    /** Convert NativeImage's ABGR integer layout into BufferedImage's ARGB layout. */
    private static int[] copyArgb(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                pixels[y * image.getWidth() + x] = (abgr & 0xFF00FF00)
                        | ((abgr & 0x00FF0000) >>> 16) | ((abgr & 0x000000FF) << 16);
            }
        }
        return pixels;
    }

    private static void restore(Minecraft minecraft, Active frame) {
        minecraft.options.hideGui = frame.previousHideGui();
        minecraft.options.setCameraType(frame.previousType());
        if (frame.previousCamera() != null) {
            minecraft.setCameraEntity(frame.previousCamera());
        } else if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }
}
