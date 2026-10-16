package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.ClientGameState;
import com.revjve.fractured.client.GhostEchoPlayer;
import com.revjve.fractured.client.render.FracturedShardRenderer;
import com.revjve.fractured.game.RoleRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Undisguised, The Fractured is drawn as its glass-shard body instead of a player model, both in
 * the world and as first-person claws.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void fractured$shardForm(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (fractured$isShardForm(player)) {
            FracturedShardRenderer.render(player, tickDelta, matrices, vertexConsumers, light);
            ci.cancel();
        }
    }

    @Inject(method = "renderRightArm", at = @At("HEAD"), cancellable = true)
    private void fractured$rightClaw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (fractured$isShardForm(player)) {
            FracturedShardRenderer.renderFirstPersonArm(matrices, vertexConsumers, light, player, true);
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"), cancellable = true)
    private void fractured$leftClaw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (fractured$isShardForm(player)) {
            FracturedShardRenderer.renderFirstPersonArm(matrices, vertexConsumers, light, player, false);
            ci.cancel();
        }
    }

    private static boolean fractured$isShardForm(AbstractClientPlayerEntity player) {
        return !(player instanceof GhostEchoPlayer)
                && RoleRegistry.isFracturedClient(player.getUuid())
                && !ClientGameState.disguises.containsKey(player.getUuid());
    }
}
