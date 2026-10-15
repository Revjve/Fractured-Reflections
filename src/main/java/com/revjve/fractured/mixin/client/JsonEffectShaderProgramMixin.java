package com.revjve.fractured.mixin.client;

import net.minecraft.client.gl.JsonEffectShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla 1.20.1 resolves post-effect programs as {@code new Identifier("shaders/program/" + name)},
 * which breaks for namespaced names such as {@code fractured_reflections:composite}. Rewrite
 * {@code shaders/program/ns:name.ext} to {@code ns:shaders/program/name.ext}. Strings already
 * fixed by another mod (e.g. Satin applies the same patch) pass through untouched.
 * {@code require = 0}: if the target ever moves, our post effect simply fails to load and is
 * disabled instead of crashing the client.
 */
@Mixin(JsonEffectShaderProgram.class)
public abstract class JsonEffectShaderProgramMixin {
    private static final String PREFIX = "shaders/program/";

    @ModifyArg(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;<init>(Ljava/lang/String;)V"))
    private String fractured$namespacedProgram(String path) {
        return fractured$fix(path);
    }

    @ModifyArg(method = "loadEffect", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;<init>(Ljava/lang/String;)V"))
    private static String fractured$namespacedStage(String path) {
        return fractured$fix(path);
    }

    private static String fractured$fix(String path) {
        if (!path.startsWith(PREFIX)) return path;
        String rest = path.substring(PREFIX.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return path;
        return rest.substring(0, colon) + ":" + PREFIX + rest.substring(colon + 1);
    }
}
