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
import net.minecraft.util.math.random.Random;

/**
 * Two post chains, each skipped entirely when idle:
 * <ol>
 *   <li>{@code mirror_realm} — only inside the Mirror Dimension: bright-pass, two-scale separable
 *       gaussian bloom, then the crimson-and-soot grade (filmic red ramp, red bloom, heat shimmer,
 *       grain, vignette, signal tears, heartbeat flashes).</li>
 *   <li>{@code fractured} — everywhere: sanity warp + chromatic split, shattered-glass refraction
 *       overlay (Voronoi), flashlight cone and flicker, one-shot flashes.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class FRPostEffects {
    private static final Chain MIRROR = new Chain(FracturedReflections.id("shaders/post/mirror_realm.json"));
    private static final Chain COMPOSITE = new Chain(FracturedReflections.id("shaders/post/fractured.json"));
    private static final Random RANDOM = Random.create();

    private static float mirror, distortion, flashlight, flicker, cracks, tear;
    private static float prevMirror, prevDistortion, prevFlashlight, prevFlicker, prevCracks;
    private static int tearTicks;

    private FRPostEffects() {}

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return FracturedReflections.id("post_effects");
            }

            @Override
            public void reload(ResourceManager manager) {
                MIRROR.close();
                COMPOSITE.close();
            }
        });
    }

    public static void tick(MinecraftClient client) {
        prevMirror = mirror;
        prevDistortion = distortion;
        prevFlashlight = flashlight;
        prevFlicker = flicker;
        prevCracks = cracks;
        boolean hunting = ClientGameState.phase == MatchPhase.HUNT || ClientGameState.phase == MatchPhase.PREPARING;
        boolean survivor = hunting && ClientGameState.isLocalSurvivor() && client.player != null;
        boolean inMirror = ClientGameState.inMirrorDimension();

        mirror = approach(mirror, inMirror ? 1f : 0f, 0.04f);

        float sanity = ClientGameState.sanity / Math.max(1f, ClientGameState.sanityMax);
        float targetDistortion = survivor ? MathHelper.clamp((0.55f - sanity) / 0.55f, 0f, 1f) : 0f;
        distortion = approach(distortion, Math.max(targetDistortion, ClientGameState.dragPulse), 0.03f);

        float targetCracks = (inMirror ? 0.28f : 0f) + (survivor && sanity < 0.25f ? 0.35f : 0f);
        targetCracks = Math.max(targetCracks, Math.max(ClientGameState.dragPulse, ClientGameState.crackPulse));
        cracks = approach(cracks, MathHelper.clamp(targetCracks, 0f, 1f), 0.05f);

        boolean lit = survivor && FlashlightItem.isLitInHand(client.player);
        flashlight = approach(flashlight, lit ? 1f : 0f, 0.15f);
        flicker = lit ? 1f - ClientGameState.flashlightIntensity(client.player) : 0f;

        // the reflection struggles to hold together, worse when the killer is close
        if (tearTicks > 0) tearTicks--;
        else if (inMirror && RANDOM.nextFloat() < 0.006f + ClientGameState.fracturedProximity * 0.05f) tearTicks = 3 + RANDOM.nextInt(6);
        tear = tearTicks > 0 ? 1f : 0f;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** Invoked from GameRendererMixin right after the world (and entity outlines) are drawn. */
    public static void render(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        float m = MathHelper.lerp(tickDelta, prevMirror, mirror);
        float d = MathHelper.lerp(tickDelta, prevDistortion, distortion);
        float f = MathHelper.lerp(tickDelta, prevFlashlight, flashlight);
        float fl = MathHelper.lerp(tickDelta, prevFlicker, flicker);
        float c = MathHelper.lerp(tickDelta, prevCracks, cracks);
        float pulse = ClientGameState.screenPulse;
        float heartbeat = ClientGameState.heartbeatFlash;
        float time = client.world == null ? 0f : ((client.world.getTime() % 24000L) + tickDelta) / 20f;

        if (m > 0.005f && MIRROR.prepare(client)) {
            MIRROR.set("GameTime", time);
            MIRROR.set("Intensity", m);
            MIRROR.set("Tear", tear);
            MIRROR.set("Pulse", Math.max(heartbeat, pulse * 0.5f));
            MIRROR.draw(client, tickDelta);
        }
        if ((d > 0.005f || f > 0.005f || fl > 0.005f || c > 0.005f || pulse > 0.005f) && COMPOSITE.prepare(client)) {
            COMPOSITE.set("GameTime", time);
            COMPOSITE.set("MirrorAmount", m);
            COMPOSITE.set("Distortion", d);
            COMPOSITE.set("Cracks", c);
            COMPOSITE.set("Flashlight", f);
            COMPOSITE.set("Flicker", fl);
            COMPOSITE.set("Pulse", pulse);
            COMPOSITE.draw(client, tickDelta);
        }
    }

    /** FOV multiplier for low-sanity "breathing" distortion (applied by AbstractClientPlayerEntityMixin). */
    public static float fovMultiplier(float tickDelta) {
        float d = MathHelper.lerp(tickDelta, prevDistortion, distortion);
        if (d <= 0.001f) return 1f;
        float t = (ClientGameState.clientTicks + tickDelta) * 0.09f;
        return 1f + d * (0.07f * MathHelper.sin(t) + 0.03f * MathHelper.sin(t * 2.7f));
    }

    /** One lazily loaded post chain that follows the main framebuffer size. */
    private static final class Chain {
        private final Identifier id;
        private PostEffectProcessor processor;
        private boolean failed;
        private int width = -1;
        private int height = -1;

        Chain(Identifier id) {
            this.id = id;
        }

        boolean prepare(MinecraftClient client) {
            if (failed) return false;
            Framebuffer framebuffer = client.getFramebuffer();
            if (processor == null) {
                try {
                    processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), framebuffer, id);
                } catch (Exception e) {
                    failed = true;
                    FracturedReflections.LOGGER.warn("Failed to load post effect {}; it will be skipped", id, e);
                    return false;
                }
                width = height = -1;
            }
            if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
                width = framebuffer.textureWidth;
                height = framebuffer.textureHeight;
                processor.setupDimensions(width, height);
            }
            return true;
        }

        void set(String name, float value) {
            for (PostEffectPass pass : ((PostEffectProcessorAccessor) processor).fractured$getPasses()) {
                pass.getProgram().getUniformByNameOrDummy(name).set(value);
            }
        }

        void draw(MinecraftClient client, float tickDelta) {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            processor.render(tickDelta);
            client.getFramebuffer().beginWrite(true);
        }

        void close() {
            if (processor != null) processor.close();
            processor = null;
            failed = false;
            width = height = -1;
        }
    }
}
