package com.revjve.fractured.client;

import com.revjve.fractured.client.hud.FRHud;
import com.revjve.fractured.client.render.FRPostEffects;
import com.revjve.fractured.client.render.GlassEchoRenderer;
import com.revjve.fractured.client.render.GlassMoteParticle;
import com.revjve.fractured.client.render.MirrorBlockEntityRenderer;
import com.revjve.fractured.client.render.MirrorDimensionEffects;
import com.revjve.fractured.registry.ModBlockEntities;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModEntities;
import com.revjve.fractured.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

@Environment(EnvType.CLIENT)
public final class FracturedReflectionsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MIRROR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ESCAPE_MIRROR, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.REFRACTION_LENS, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RITUAL_ALTAR, RenderLayer.getCutout());

        BlockEntityRendererFactories.register(ModBlockEntities.MIRROR, MirrorBlockEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.GLASS_ECHO, GlassEchoRenderer::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.GLASS_MOTE, GlassMoteParticle.Factory::new);
        DimensionRenderingRegistry.registerDimensionEffects(ModDimensions.MIRROR_ID, new MirrorDimensionEffects());

        FRKeybinds.init();
        FRPostEffects.registerReloadListener();
        ClientNetworking.registerReceivers();
        HudRenderCallback.EVENT.register(FRHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(FracturedReflectionsClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ClientGameState.reset();
            GhostEchoManager.clear(client);
        }));
    }

    private static void tick(MinecraftClient client) {
        ClientGameState.clientTicks++;
        if (ClientGameState.phaseTicks > 0) ClientGameState.phaseTicks--;
        ClientGameState.screenPulse = Math.max(0f, ClientGameState.screenPulse - 0.04f);
        ClientGameState.dragPulse = Math.max(0f, ClientGameState.dragPulse - 0.02f);
        ClientGameState.reveals.removeIf(r -> r.expiresAt() < ClientGameState.clientTicks);
        ClientGameState.crawls.values().removeIf(c -> ClientGameState.clientTicks - c.startedAt() > c.duration());
        for (int n = 0; n < ClientGameState.cooldowns.length; n++) {
            if (ClientGameState.cooldowns[n] > 0) ClientGameState.cooldowns[n]--;
        }

        FRKeybinds.tick(client);
        ClientGameState.updateProximity(client);
        HallucinationController.tick(client);
        MirrorAtmosphere.tick(client);
        FRPostEffects.tick(client);
    }
}
