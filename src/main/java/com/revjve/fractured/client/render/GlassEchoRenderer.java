package com.revjve.fractured.client.render;

import com.revjve.fractured.entity.GlassEchoEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Draws a Glass Echo with the mimicked survivor's skin and arm geometry (wide or slim). */
@Environment(EnvType.CLIENT)
public class GlassEchoRenderer extends LivingEntityRenderer<GlassEchoEntity, PlayerEntityModel<GlassEchoEntity>> {
    private final PlayerEntityModel<GlassEchoEntity> wide;
    private final PlayerEntityModel<GlassEchoEntity> slim;

    public GlassEchoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(GlassEchoEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = isSlim(entity) ? slim : wide;
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(GlassEchoEntity entity) {
        PlayerListEntry entry = entry(entity);
        if (entry != null) return entry.getSkinTexture();
        return DefaultSkinHelper.getTexture(entity.getMimicked().orElse(entity.getUuid()));
    }

    @Override
    protected boolean hasLabel(GlassEchoEntity entity) {
        return entity.hasCustomName() && MinecraftClient.isHudEnabled();
    }

    private static boolean isSlim(GlassEchoEntity entity) {
        PlayerListEntry entry = entry(entity);
        if (entry != null) return "slim".equals(entry.getModel());
        return "slim".equals(DefaultSkinHelper.getModel(entity.getMimicked().orElse(entity.getUuid())));
    }

    @Nullable
    private static PlayerListEntry entry(GlassEchoEntity entity) {
        UUID uuid = entity.getMimicked().orElse(null);
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        return uuid == null || handler == null ? null : handler.getPlayerListEntry(uuid);
    }
}
