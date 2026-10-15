package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.render.FRPostEffects;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the composite post pass after the world is rendered and before the HUD, like vanilla's own post effects. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V", shift = At.Shift.AFTER))
    private void fractured$postEffects(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        FRPostEffects.render(tickDelta);
    }
}
