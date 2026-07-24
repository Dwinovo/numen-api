package com.dwinovo.numen.mixin;

import com.dwinovo.numen.client.vision.VisionObservationCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-thread hook used solely for ephemeral Numen first-person observations. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"))
    private void numen$beginVisualObservation(DeltaTracker deltaTracker, boolean renderLevel,
                                               CallbackInfo ci) {
        if (renderLevel) VisionObservationCapture.beginFrame(minecraft);
    }

    /**
     * Vanilla invokes this private screenshot checkpoint immediately after renderLevel. Sampling
     * after it gives us the complete world colour buffer while still preceding HUD/screen drawing.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V",
            shift = At.Shift.AFTER))
    private void numen$captureVisualObservation(DeltaTracker deltaTracker, boolean renderLevel,
                                                 CallbackInfo ci) {
        VisionObservationCapture.captureFrame(minecraft);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void numen$restoreVisualObservation(DeltaTracker deltaTracker, boolean renderLevel,
                                                 CallbackInfo ci) {
        VisionObservationCapture.endFrameWithoutCapture(minecraft);
    }
}
