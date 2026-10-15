package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.DisguiseHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Identity Spoofing for nametags: client-side only, so server logic still sees the real name. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void fractured$spoofName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!self.getWorld().isClient) return;
        PlayerListEntry entry = DisguiseHelper.disguiseEntry(self);
        if (entry != null) cir.setReturnValue(DisguiseHelper.displayName(entry));
    }
}
