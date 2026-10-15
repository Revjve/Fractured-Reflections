package com.revjve.fractured.mixin.client;

import com.revjve.fractured.client.sound.MuffleFilter;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Muffled audio in the Mirror Dimension: attaches an OpenAL EFX low-pass filter to each source as it starts. */
@Mixin(Source.class)
public abstract class SourceMixin {
    @Shadow
    @Final
    private int pointer;

    @Inject(method = "play", at = @At("HEAD"))
    private void fractured$muffle(CallbackInfo ci) {
        MuffleFilter.apply(pointer);
    }
}
