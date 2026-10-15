package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.DisguiseHelper;
import com.revjve.fractured.client.render.FRPostEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shapeshifting: a disguised Fractured reports the copied survivor's skin, cape and model type
 * ("default"/"slim" geometry), so every renderer — including Simple Voice Chat's talk icon over
 * the entity — presents it as that survivor. Also applies low-sanity FOV breathing to the local player.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void fractured$disguiseSkin(CallbackInfoReturnable<Identifier> cir) {
        PlayerListEntry entry = DisguiseHelper.disguiseEntry((AbstractClientPlayerEntity) (Object) this);
        if (entry != null) cir.setReturnValue(entry.getSkinTexture());
    }

    @Inject(method = "getCapeTexture", at = @At("HEAD"), cancellable = true)
    private void fractured$disguiseCape(CallbackInfoReturnable<Identifier> cir) {
        PlayerListEntry entry = DisguiseHelper.disguiseEntry((AbstractClientPlayerEntity) (Object) this);
        if (entry != null) cir.setReturnValue(entry.getCapeTexture());
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void fractured$disguiseModel(CallbackInfoReturnable<String> cir) {
        PlayerListEntry entry = DisguiseHelper.disguiseEntry((AbstractClientPlayerEntity) (Object) this);
        if (entry != null) cir.setReturnValue(entry.getModel());
    }

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void fractured$sanityFov(CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this == client.player) {
            cir.setReturnValue(cir.getReturnValueF() * FRPostEffects.fovMultiplier(client.getTickDelta()));
        }
    }
}
