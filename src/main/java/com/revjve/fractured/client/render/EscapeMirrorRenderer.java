package com.revjve.fractured.client.render;

import com.revjve.fractured.block.EscapeMirrorBlock;
import com.revjve.fractured.block.EscapeMirrorBlockEntity;
import com.revjve.fractured.registry.ModBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Escape mirror: the glass is a window into the vanilla end-gateway starfield, a whirlpool of
 * cold blue light spins in front of it, and a tall pale-blue column rises from it so a trapped
 * survivor can find it across the crimson dark.
 */
@Environment(EnvType.CLIENT)
public class EscapeMirrorRenderer implements BlockEntityRenderer<EscapeMirrorBlockEntity> {
    private static final float MIN = 2f / 16f, MAX = 14f / 16f, SURFACE_Z = 13.9f / 16f;

    public EscapeMirrorRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(EscapeMirrorBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (entity.getWorld() == null) return;
        BlockState state = entity.getCachedState();
        if (!state.contains(EscapeMirrorBlock.FACING)) return;
        Direction facing = state.get(EscapeMirrorBlock.FACING);
        float time = (entity.getWorld().getTime() % 100000L) + tickDelta;
        boolean bottom = !entity.getWorld().getBlockState(entity.getPos().down()).isOf(ModBlocks.ESCAPE_MIRROR);

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()));
        matrices.translate(-0.5, -0.5, -0.5);

        VertexConsumer gateway = consumers.getBuffer(RenderLayer.getEndGateway());
        Matrix4f pos = matrices.peek().getPositionMatrix();
        gateway.vertex(pos, MIN, MAX, SURFACE_Z).next();
        gateway.vertex(pos, MAX, MAX, SURFACE_Z).next();
        gateway.vertex(pos, MAX, MIN, SURFACE_Z).next();
        gateway.vertex(pos, MIN, MIN, SURFACE_Z).next();

        // whirlpool (drawn once, by the bottom block, at the centre of the two-block mirror)
        VertexConsumer glow = consumers.getBuffer(FRRenderUtil.glowLayer());
        for (int n = 0; bottom && n < 4; n++) {
            matrices.push();
            matrices.translate(0.5f, 1.0f, SURFACE_Z - 0.02f - n * 0.015f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(time * (0.05f + n * 0.03f) * (n % 2 == 0 ? 1f : -1f)));
            float size = 0.55f - n * 0.11f;
            float pulse = 0.6f + 0.4f * MathHelper.sin(time * 0.2f + n);
            FRRenderUtil.flare(matrices, glow, size, 0.35f, 0.75f, 1f, 0.35f * pulse);
            matrices.pop();
        }
        matrices.pop();

        if (bottom) {
            VertexConsumer beams = consumers.getBuffer(FRRenderUtil.beamLayer());
            float sway = MathHelper.sin(time * 0.05f) * 0.03f;
            Vector3f base = new Vector3f(0.5f, 1.0f, 0.5f);
            Vector3f top = new Vector3f(0.5f + sway, 14f, 0.5f - sway);
            FRRenderUtil.beam(matrices, beams, base, top, 0.06f, 0.6f, 0.85f, 1f, 0.8f, -time * 0.04f);
            FRRenderUtil.beam(matrices, beams, base, top, 0.25f, 0.3f, 0.6f, 1f, 0.18f, -time * 0.015f);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(EscapeMirrorBlockEntity entity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }
}
