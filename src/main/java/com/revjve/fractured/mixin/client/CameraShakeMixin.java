package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.ClientGameState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake (blackouts, portals tearing open, being dragged, the killer crawling out of a
 * mirror nearby). Applied where vanilla applies its hurt-tilt, so it composes with view bobbing.
 */
@Mixin(GameRenderer.class)
public abstract class CameraShakeMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void fractured$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float s = ClientGameState.shake;
        if (s <= 0.001f) return;
        float t = ClientGameState.clientTicks + tickDelta;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((MathHelper.sin(t * 2.3f) + MathHelper.sin(t * 5.1f) * 0.5f) * s * 2.2f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((MathHelper.cos(t * 2.9f) + MathHelper.sin(t * 6.7f) * 0.4f) * s * 1.6f));
    }
}
