package com.revjve.fractured.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.client.ClientGameState;
import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.item.FlashlightItem;
import com.revjve.fractured.mixin.client.PostEffectProcessorAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * One lightweight full-screen pass ({@code shaders/program/composite.fsh}) that handles every
 * screen-space effect: Mirror Dimension desaturation/cold grade, sanity warping + chromatic split,
 * the flashlight cone vignette and its flicker, and one-shot flashes. It is skipped entirely when
 * all effects are idle, so outside a match it costs nothing.
 */
@Environment(EnvType.CLIENT)
public final class FRPostEffects {
    private static final Identifier CHAIN = FracturedReflections.id("shaders/post/fractured.json");

    private static PostEffectProcessor processor;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;

    private static float mirror, distortion, flashlight, flicker;
    private static float prevMirror, prevDistortion, prevFlashlight, prevFlicker;

    private FRPostEffects() {}

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return FracturedReflections.id("post_effects");
            }

            @Override
            public void reload(ResourceManager manager) {
                close();
                failed = false;
            }
        });
    }

    public static void tick(MinecraftClient client) {
        prevMirror = mirror;
        prevDistortion = distortion;
        prevFlashlight = flashlight;
        prevFlicker = flicker;
        boolean hunting = ClientGameState.phase == MatchPhase.HUNT || ClientGameState.phase == MatchPhase.PREPARING;
        boolean survivor = hunting && ClientGameState.isLocalSurvivor() && client.player != null;

        mirror = approach(mirror, ClientGameState.inMirrorDimension() ? 1f : 0f, 0.04f);

        float sanity = ClientGameState.sanity / Math.max(1f, ClientGameState.sanityMax);
        float targetDistortion = survivor ? MathHelper.clamp((0.55f - sanity) / 0.55f, 0f, 1f) : 0f;
        distortion = approach(distortion, Math.max(targetDistortion, ClientGameState.dragPulse), 0.03f);

        boolean lit = survivor && FlashlightItem.isLitInHand(client.player);
        flashlight = approach(flashlight, lit ? 1f : 0f, 0.15f);
        flicker = lit ? 1f - ClientGameState.flashlightIntensity(client.player) : 0f;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** Invoked from GameRendererMixin right after the world (and entity outlines) are drawn. */
    public static void render(float tickDelta) {
        if (failed) return;
        float m = MathHelper.lerp(tickDelta, prevMirror, mirror);
        float d = MathHelper.lerp(tickDelta, prevDistortion, distortion);
        float f = MathHelper.lerp(tickDelta, prevFlashlight, flashlight);
        float fl = MathHelper.lerp(tickDelta, prevFlicker, flicker);
        float pulse = ClientGameState.screenPulse;
        if (m < 0.005f && d < 0.005f && f < 0.005f && fl < 0.005f && pulse < 0.005f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (processor == null) load(client, framebuffer);
        if (processor == null) return;
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            width = framebuffer.textureWidth;
            height = framebuffer.textureHeight;
            processor.setupDimensions(width, height);
        }

        float time = client.world == null ? 0f : ((client.world.getTime() % 24000L) + tickDelta) / 20f;
        for (PostEffectPass pass : ((PostEffectProcessorAccessor) processor).fractured$getPasses()) {
            var program = pass.getProgram();
            program.getUniformByNameOrDummy("GameTime").set(time);
            program.getUniformByNameOrDummy("MirrorAmount").set(m);
            program.getUniformByNameOrDummy("Distortion").set(d);
            program.getUniformByNameOrDummy("Flashlight").set(f);
            program.getUniformByNameOrDummy("Flicker").set(fl);
            program.getUniformByNameOrDummy("Pulse").set(pulse);
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        processor.render(tickDelta);
        framebuffer.beginWrite(true);
    }

    /** FOV multiplier for low-sanity "breathing" distortion (applied by AbstractClientPlayerEntityMixin). */
    public static float fovMultiplier(float tickDelta) {
        float d = MathHelper.lerp(tickDelta, prevDistortion, distortion);
        if (d <= 0.001f) return 1f;
        float t = (ClientGameState.clientTicks + tickDelta) * 0.09f;
        return 1f + d * (0.07f * MathHelper.sin(t) + 0.03f * MathHelper.sin(t * 2.7f));
    }

    private static void load(MinecraftClient client, Framebuffer framebuffer) {
        try {
            processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), framebuffer, CHAIN);
            width = framebuffer.textureWidth;
            height = framebuffer.textureHeight;
            processor.setupDimensions(width, height);
        } catch (Exception e) {
            failed = true;
            processor = null;
            FracturedReflections.LOGGER.warn("Failed to load post effect {}; screen effects disabled", CHAIN, e);
        }
    }

    private static void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        width = height = -1;
    }
}
