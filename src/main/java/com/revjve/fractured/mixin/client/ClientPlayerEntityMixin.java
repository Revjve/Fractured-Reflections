package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.ClientGameState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stamina: an exhausted survivor cannot start or keep sprinting (the server enforces it too). */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "canSprint", at = @At("RETURN"), cancellable = true)
    private void fractured$staminaGate(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ClientGameState.exhausted && ClientGameState.isLocalSurvivor()) {
            cir.setReturnValue(false);
        }
    }
}
