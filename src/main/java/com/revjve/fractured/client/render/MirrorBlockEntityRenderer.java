package com.revjve.fractured.client.render;

import com.revjve.fractured.block.MirrorBlock;
import com.revjve.fractured.block.MirrorBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Mirror surface. Idle: a silvered glass sheet (vanilla glass texture, tinted) with the vanilla
 * enchantment-glint shader sweeping across it — a cheap, premade "planar sheen" that never shows
 * the other dimension. Transformed: the vanilla end-portal parallax shader, i.e. an open rift.
 * (With Immersive Portals installed the rift is additionally a real see-through portal, and the
 * optional IP Mirror entities give true reflections.)
 */
@Environment(EnvType.CLIENT)
public class MirrorBlockEntityRenderer implements BlockEntityRenderer<MirrorBlockEntity> {
    private static final Identifier GLASS = new Identifier("textures/block/glass.png");
    private static final float MIN = 2f / 16f;
    private static final float MAX = 14f / 16f;
    /** Just in front of the glass pane (14/16), behind the frame lip (13/16). */
    private static final float SURFACE_Z = 13.9f / 16f;

    public MirrorBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(MirrorBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!state.contains(MirrorBlock.FACING)) return;
        Direction facing = state.get(MirrorBlock.FACING);

        matrices.push();
        // the surface is authored facing north (-Z); rotate it to the block's facing
        matrices.translate(0.5, 0.5, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()));
        matrices.translate(-0.5, -0.5, -0.5);
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        if (state.get(MirrorBlock.ACTIVE)) {
            VertexConsumer rift = vertexConsumers.getBuffer(RenderLayer.getEndPortal());
            rift.vertex(pos, MIN, MAX, SURFACE_Z).next();
            rift.vertex(pos, MAX, MAX, SURFACE_Z).next();
            rift.vertex(pos, MAX, MIN, SURFACE_Z).next();
            rift.vertex(pos, MIN, MIN, SURFACE_Z).next();
        } else {
            VertexConsumer glass = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(GLASS));
            glassVertex(glass, pos, normal, MIN, MAX, 0f, 0f, light);
            glassVertex(glass, pos, normal, MAX, MAX, 1f, 0f, light);
            glassVertex(glass, pos, normal, MAX, MIN, 1f, 1f, light);
            glassVertex(glass, pos, normal, MIN, MIN, 0f, 1f, light);

            VertexConsumer sheen = vertexConsumers.getBuffer(RenderLayer.getEntityGlint());
            float z = SURFACE_Z - 0.001f;
            sheen.vertex(pos, MIN, MAX, z).texture(0f, 0f).next();
            sheen.vertex(pos, MAX, MAX, z).texture(0.5f, 0f).next();
            sheen.vertex(pos, MAX, MIN, z).texture(0.5f, 0.5f).next();
            sheen.vertex(pos, MIN, MIN, z).texture(0f, 0.5f).next();
        }
        matrices.pop();
    }

    private static void glassVertex(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float x, float y, float u, float v, int light) {
        vc.vertex(pos, x, y, SURFACE_Z)
                .color(0.78f, 0.84f, 0.92f, 0.62f)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, 0f, 0f, -1f)
                .next();
    }
}
