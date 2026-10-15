package com.revjve.fractured.client.render;

import com.revjve.fractured.client.ClientGameState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The Fractured's true form: ~60 disjointed glass shards hovering in a humanoid silhouette.
 * Shards drift and turn independently, limbs swing with the walk cycle, and each shard's facing
 * is tested against the view direction every frame — shards that catch the eye flare with an
 * emissive glint, so the killer sparkles in the dark as the camera or the shards move.
 * Uses vanilla's ice texture and entity shaders (premade assets only).
 *
 * <p>Mirror Crawl: right after crossing a mirror the body unfolds out of the glass behind it —
 * compressed against the mirror plane, pitched forward on all fours, rising over ~1.5 s.
 */
@Environment(EnvType.CLIENT)
public final class FracturedShardRenderer {
    private static final Identifier TEXTURE = new Identifier("textures/block/ice.png");

    /** part, x, y, z, width, height, rx, ry, rz, phase, u, v */
    private static final float[][] SHARDS;
    private static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    /** minX, minY, minZ, maxX, maxY, maxZ per part (model faces -Z). */
    private static final float[][] PART_BOX = {
            {-0.25f, 1.50f, -0.25f, 0.25f, 2.00f, 0.25f},
            {-0.25f, 0.75f, -0.13f, 0.25f, 1.50f, 0.13f},
            {-0.50f, 0.78f, -0.12f, -0.27f, 1.48f, 0.12f},
            {0.27f, 0.78f, -0.12f, 0.50f, 1.48f, 0.12f},
            {-0.24f, 0.00f, -0.12f, -0.02f, 0.75f, 0.12f},
            {0.02f, 0.00f, -0.12f, 0.24f, 0.75f, 0.12f}};
    private static final int[] COUNTS = {12, 16, 8, 8, 8, 8};

    static {
        Random random = Random.create(0x6C4A55L);
        int total = 0;
        for (int c : COUNTS) total += c;
        SHARDS = new float[total][];
        int n = 0;
        for (int part = 0; part < COUNTS.length; part++) {
            float[] b = PART_BOX[part];
            for (int k = 0; k < COUNTS[part]; k++) {
                SHARDS[n++] = new float[]{
                        part,
                        MathHelper.lerp(random.nextFloat(), b[0], b[3]),
                        MathHelper.lerp(random.nextFloat(), b[1], b[4]),
                        MathHelper.lerp(random.nextFloat(), b[2], b[5]),
                        0.08f + random.nextFloat() * 0.16f,
                        0.10f + random.nextFloat() * 0.20f,
                        random.nextFloat() * MathHelper.TAU,
                        random.nextFloat() * MathHelper.TAU,
                        random.nextFloat() * MathHelper.TAU,
                        random.nextFloat() * MathHelper.TAU,
                        random.nextInt(4) * 0.25f,
                        random.nextInt(4) * 0.25f};
            }
        }
    }

    private FracturedShardRenderer() {}

    public static void render(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        float limbPos = player.limbAnimator.getPos(tickDelta);
        float limbSpeed = Math.min(1f, player.limbAnimator.getSpeed(tickDelta));
        float time = player.age + tickDelta;

        ClientGameState.Crawl crawl = ClientGameState.crawls.get(player.getId());
        float progress = crawl == null ? 1f
                : MathHelper.clamp((ClientGameState.clientTicks - crawl.startedAt() + tickDelta) / Math.max(1, crawl.duration()), 0f, 1f);
        float emerge = 1f - progress;
        emerge = emerge * emerge * (3f - 2f * emerge);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        if (emerge > 0f) {
            matrices.translate(0f, 0f, emerge * 0.9f);                                   // still inside the mirror behind
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75f * emerge));   // crawling posture
            matrices.scale(1f, 0.55f + 0.45f * progress, 0.4f + 0.6f * progress);        // flattened against the glass
        } else if (player.getAbilities().flying) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12f));
        }
        if (player.isInSneakingPose()) matrices.translate(0f, -0.15f, 0f);

        VertexConsumer base = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        VertexConsumer glint = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        float alpha = 0.72f * (0.35f + 0.65f * progress);
        float swing = MathHelper.cos(limbPos * 0.6662f) * 1.2f * limbSpeed;

        for (float[] s : SHARDS) {
            int part = (int) s[0];
            matrices.push();
            switch (part) {
                case HEAD -> {
                    pivot(matrices, 0f, 1.5f, 0f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - headYaw));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
                    pivot(matrices, 0f, -1.5f, 0f);
                }
                case RIGHT_ARM, LEFT_ARM -> {
                    float x = part == RIGHT_ARM ? -0.375f : 0.375f;
                    pivot(matrices, x, 1.4f, 0f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotation(part == RIGHT_ARM ? swing : -swing));
                    pivot(matrices, -x, -1.4f, 0f);
                }
                case RIGHT_LEG, LEFT_LEG -> {
                    float x = part == RIGHT_LEG ? -0.125f : 0.125f;
                    pivot(matrices, x, 0.75f, 0f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotation(part == RIGHT_LEG ? -swing : swing));
                    pivot(matrices, -x, -0.75f, 0f);
                }
                default -> { }
            }

            float[] box = PART_BOX[part];
            float cx = (box[0] + box[3]) * 0.5f, cy = (box[1] + box[4]) * 0.5f, cz = (box[2] + box[5]) * 0.5f;
            float phase = s[9];
            float drift = 1.12f + 0.06f * MathHelper.sin(time * 0.07f + phase);   // disjointed: shards hover apart
            float bob = 0.02f * MathHelper.sin(time * 0.11f + phase * 1.7f);
            matrices.translate(cx + (s[1] - cx) * drift, cy + (s[2] - cy) * drift + bob, cz + (s[3] - cz) * drift);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(s[7] + time * 0.015f * MathHelper.sin(phase)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(s[6] + 0.25f * MathHelper.sin(time * 0.05f + phase)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(s[8]));

            Matrix4f pos = matrices.peek().getPositionMatrix();
            Matrix3f normal = matrices.peek().getNormalMatrix();
            float spec = specular(pos, normal);
            emitShard(base, pos, normal, s, 0.70f, 0.86f, 1.0f, alpha, light);
            if (spec > 0.06f) emitShard(glint, pos, normal, s, 1f, 1f, 1f, Math.min(1f, spec), 0xF000F0);
            matrices.pop();
        }
        matrices.pop();
    }

    private static void pivot(MatrixStack matrices, float x, float y, float z) {
        matrices.translate(x, y, z);
    }

    /** Blinn-ish highlight for a light at the eye: shards facing the camera flash. */
    private static float specular(Matrix4f pos, Matrix3f normal) {
        Vector4f p = new Vector4f(0f, 0f, 0f, 1f).mul(pos);
        Vector3f view = new Vector3f(-p.x(), -p.y(), -p.z());
        if (view.lengthSquared() < 1.0E-6f) return 0f;
        view.normalize();
        Vector3f n = new Vector3f(0f, 0f, 1f).mul(normal).normalize();
        float d = Math.abs(n.dot(view));
        return (float) Math.pow(d, 24.0);
    }

    /** A jagged triangle (degenerate quad) of glass. */
    private static void emitShard(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float[] s, float r, float g, float b, float a, int light) {
        float hw = s[4] * 0.5f, hh = s[5] * 0.5f, u = s[10], v = s[11];
        vertex(vc, pos, normal, -hw, -hh, u, v + 0.25f, r, g, b, a, light);
        vertex(vc, pos, normal, hw, -hh, u + 0.25f, v + 0.25f, r, g, b, a, light);
        vertex(vc, pos, normal, hw * 0.2f, hh, u + 0.15f, v, r, g, b, a, light);
        vertex(vc, pos, normal, hw * 0.2f, hh, u + 0.15f, v, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float x, float y, float u, float v,
                               float r, float g, float b, float a, int light) {
        vc.vertex(pos, x, y, 0f).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0f, 0f, 1f).next();
    }
}
