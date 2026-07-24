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
 * turns originate on the client thread, so {@link #request(UUID)} queues a capture and
 * {@code MixinGameRenderer} brackets the next world render with {@link #beginFrame(Minecraft)} and
 * {@link #captureFrame(Minecraft)}. The owner's camera settings are restored immediately after the
 * framebuffer read. The expensive resize/JPEG encode runs on Minecraft's I/O pool.
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

    private record Request(UUID entityUuid, CompletableFuture<VisualObservation> future) {}

    private record Active(Request request, Entity previousCamera, CameraType previousType,
                          boolean previousHideGui) {}

    /** Only touched on the render/client thread after it is assigned by {@link #beginFrame}. */
    private static Active active;

    private VisionObservationCapture() {}

    /** Queue one fresh frame. A missing/unloaded body or a render timeout resolves to {@code null}. */
    public static CompletableFuture<VisualObservation> request(UUID entityUuid) {
        if (entityUuid == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<VisualObservation> future = new CompletableFuture<>();
        synchronized (QUEUE) {
            while (QUEUE.size() >= MAX_QUEUED) {
                Request dropped = QUEUE.pollFirst();
                if (dropped != null) dropped.future().complete(null);
            }
            QUEUE.addLast(new Request(entityUuid, future));
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

        byte[] png = null;
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            png = image.asByteArray();
        } catch (Exception ex) {
            Constants.LOG.warn("[numen-vision] framebuffer capture failed: {}", ex.toString());
        } finally {
            restore(minecraft, frame);
            active = null;
        }

        if (png == null) {
            frame.request().future().complete(null);
            return;
        }
        byte[] encodedSource = png;
        CompletableFuture.supplyAsync(() -> {
            try {
                return VisionImageEncoder.encode(encodedSource);
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
