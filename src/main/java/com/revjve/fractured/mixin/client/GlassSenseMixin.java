package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.ClientGameState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glass Sense: The Fractured perceives survivors whose sanity is breaking. They get the vanilla
 * entity outline (seen through walls), tinted blood red by {@link EntityOutlineColorMixin}.
 */
@Mixin(MinecraftClient.class)
public abstract class GlassSenseMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void fractured$glassSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerEntity && ClientGameState.isLocalFractured() && ClientGameState.panicking.contains(entity.getUuid())) {
            cir.setReturnValue(true);
        }
    }
}
