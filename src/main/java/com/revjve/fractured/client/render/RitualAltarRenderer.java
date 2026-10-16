package com.revjve.fractured.client.render;

import com.revjve.fractured.block.BeamTracer;
import com.revjve.fractured.block.RitualAltarBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/**
 * Ritual altar VFX:
 * <ul>
 *   <li>Refraction lasers: the live beam path from every Lumen Prism through the lenses (traced
 *       with the same {@link BeamTracer} the server uses), a white-hot core inside a cyan glow,
 *       with a starburst where a beam strikes a focal point.</li>
 *   <li>Cleansing ring on the floor: dormant embers once the Pure Mirror is seated, then a bright
 *       progress arc that fills as survivors hold the ring, plus orbiting runes.</li>
 *   <li>A column of light that grows with the cleansing and becomes a towering pillar when the
 *       altar is cleansed.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class RitualAltarRenderer implements BlockEntityRenderer<RitualAltarBlockEntity> {
    public RitualAltarRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(RitualAltarBlockEntity altar, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (altar.getWorld() == null) return;
        float time = altar.getAge() + tickDelta;
        BlockPos origin = altar.getPos();

        if (!altar.isCompleted()) renderLasers(altar, origin, time, matrices, consumers);
        if (altar.isForged() && !altar.isCompleted()) renderRing(altar, time, matrices, consumers);
        if (altar.isCleansingStarted() || altar.isCompleted()) renderPillar(altar, time, matrices, consumers);
    }

    private static void renderLasers(RitualAltarBlockEntity altar, BlockPos origin, float time, MatrixStack matrices, VertexConsumerProvider consumers) {
        if (altar.getEmitters().isEmpty()) return;
        BeamTracer.Result trace = BeamTracer.trace(altar.getWorld(), altar.getEmitters());
        float flow = -time * 0.08f;
        float shimmer = 0.85f + 0.15f * MathHelper.sin(time * 0.6f);
        // one layer at a time: the immediate provider flushes a layer when another is requested
        VertexConsumer beams = consumers.getBuffer(FRRenderUtil.beamLayer());
        for (BeamTracer.Segment seg : trace.segments()) {
            Vector3f a = local(seg.from(), origin);
            Vector3f b = local(seg.to(), origin);
            if (seg.terminated() && !trace.litFocals().contains(seg.to())) {
                // stop at the face of the blocking block instead of its centre
                Vector3f dir = new Vector3f(b).sub(a);
                if (dir.lengthSquared() > 0.25f) b.sub(dir.normalize().mul(0.5f));
            }
            FRRenderUtil.beam(matrices, beams, a, b, 0.035f, 1f, 1f, 1f, 0.9f * shimmer, flow);
            FRRenderUtil.beam(matrices, beams, a, b, 0.11f, 0.35f, 0.85f, 1f, 0.28f * shimmer, flow * 0.6f);
        }
        if (trace.litFocals().isEmpty()) return;
        VertexConsumer glow = consumers.getBuffer(FRRenderUtil.glowLayer());
        for (BlockPos focal : trace.litFocals()) {
            Vector3f c = local(focal, origin);
            matrices.push();
            matrices.translate(c.x(), c.y() + 0.55f, c.z());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(time * 0.1f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(time * 0.07f));
            FRRenderUtil.flare(matrices, glow, 0.45f + 0.1f * MathHelper.sin(time * 0.5f), 0.7f, 0.95f, 1f, 0.75f);
            matrices.pop();
        }
    }

    private static void renderRing(RitualAltarBlockEntity altar, float time, MatrixStack matrices, VertexConsumerProvider consumers) {
        VertexConsumer glow = consumers.getBuffer(FRRenderUtil.glowLayer());
        float radius = altar.getRingRadius();
        float progress = altar.getCleanseProgress();
        boolean active = altar.isCleansingStarted();
        float breathe = 0.6f + 0.4f * MathHelper.sin(time * (active ? 0.25f : 0.08f));

        // base ring: dim ember red until the cleansing begins
        FRRenderUtil.ring(matrices, glow, 0.5f, 0.03f, 0.5f, radius - 0.08f, radius + 0.08f, time * 0.01f, MathHelper.TAU, 64,
                1f, active ? 0.55f : 0.12f, active ? 0.2f : 0.06f, 0.45f * breathe);
        FRRenderUtil.ring(matrices, glow, 0.5f, 0.025f, 0.5f, radius - 0.35f, radius - 0.3f, -time * 0.02f, MathHelper.TAU, 48,
                1f, 0.2f, 0.1f, 0.25f * breathe);
        if (active) {
            // progress arc, white-gold, filling clockwise from north
            FRRenderUtil.ring(matrices, glow, 0.5f, 0.04f, 0.5f, radius - 0.14f, radius + 0.14f, -MathHelper.HALF_PI,
                    MathHelper.TAU * progress, Math.max(2, Math.round(64 * progress)), 1f, 0.9f, 0.6f, 0.85f);
        }
        // orbiting runes
        int runes = 8;
        for (int n = 0; n < runes; n++) {
            float a = time * (active ? 0.05f : 0.015f) + n * MathHelper.TAU / runes;
            matrices.push();
            matrices.translate(0.5f + MathHelper.cos(a) * radius, 0.35f + MathHelper.sin(time * 0.1f + n) * 0.12f, 0.5f + MathHelper.sin(a) * radius);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-a + time * 0.05f));
            FRRenderUtil.flare(matrices, glow, 0.14f, 1f, active ? 0.7f : 0.25f, active ? 0.35f : 0.12f, 0.6f * breathe);
            matrices.pop();
        }
    }

    private static void renderPillar(RitualAltarBlockEntity altar, float time, MatrixStack matrices, VertexConsumerProvider consumers) {
        VertexConsumer beams = consumers.getBuffer(FRRenderUtil.beamLayer());
        boolean done = altar.isCompleted();
        float height = done ? 48f : 1.5f + altar.getCleanseProgress() * 10f;
        float pulse = 0.75f + 0.25f * MathHelper.sin(time * (done ? 0.08f : 0.3f));
        Vector3f base = new Vector3f(0.5f, 0.8f, 0.5f);
        Vector3f top = new Vector3f(0.5f, 0.8f + height, 0.5f);
        float r = 1f, g = done ? 0.92f : 0.45f, b = done ? 0.7f : 0.25f;
        FRRenderUtil.beam(matrices, beams, base, top, done ? 0.12f : 0.07f, r, g, b, 0.9f * pulse, -time * 0.05f);
        FRRenderUtil.beam(matrices, beams, base, top, done ? 0.4f : 0.22f, r, g * 0.8f, b * 0.8f, 0.22f * pulse, -time * 0.02f);
    }

    private static Vector3f local(BlockPos pos, BlockPos origin) {
        return new Vector3f(pos.getX() - origin.getX() + 0.5f, pos.getY() - origin.getY() + 0.5f, pos.getZ() - origin.getZ() + 0.5f);
    }

    @Override
    public boolean rendersOutsideBoundingBox(RitualAltarBlockEntity altar) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }
}
