package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.ClientGameState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blood-red outline colour for survivors revealed by Glass Sense (client only). */
@Mixin(Entity.class)
public abstract class EntityOutlineColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void fractured$senseColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient && ClientGameState.isLocalFractured() && ClientGameState.panicking.contains(self.getUuid())) {
            cir.setReturnValue(0xD01818);
        }
    }
}
