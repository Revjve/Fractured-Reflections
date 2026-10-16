package com.revjve.fractured.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small geometry helpers shared by the block-entity renderers (beams, rings, flares). */
@Environment(EnvType.CLIENT)
public final class FRRenderUtil {
    public static final Identifier BEAM_TEXTURE = new Identifier("textures/entity/beacon_beam.png");
    public static final Identifier SOLID_TEXTURE = new Identifier("textures/block/white_concrete.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    private FRRenderUtil() {}

    public static RenderLayer beamLayer() {
        return RenderLayer.getBeaconBeam(BEAM_TEXTURE, true);
    }

    public static RenderLayer glowLayer() {
        return RenderLayer.getEntityTranslucentEmissive(SOLID_TEXTURE);
    }

    /**
     * A square-section beam from {@code a} to {@code b} (any direction) with scrolling texture.
     * {@code scroll} offsets the V coordinate so the light appears to flow along the beam.
     */
    public static void beam(MatrixStack matrices, VertexConsumer vc, Vector3f a, Vector3f b, float halfWidth,
                            float r, float g, float bl, float alpha, float scroll) {
        Vector3f dir = new Vector3f(b).sub(a);
        float length = dir.length();
        if (length < 1.0e-4f) return;
        dir.div(length);
        Vector3f u = Math.abs(dir.y()) > 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
        Vector3f w = new Vector3f(dir).cross(u).normalize();
        u = new Vector3f(w).cross(dir).normalize();

        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        float v0 = scroll, v1 = scroll + length * 0.5f;
        Vector3f[] sides = {u, new Vector3f(u).negate(), w, new Vector3f(w).negate()};
        Vector3f[] across = {w, new Vector3f(w).negate(), new Vector3f(u).negate(), u};
        for (int s = 0; s < 4; s++) {
            Vector3f side = new Vector3f(sides[s]).mul(halfWidth);
            Vector3f other = new Vector3f(across[s]).mul(halfWidth);
            Vector3f p1 = new Vector3f(a).add(side).sub(other);
            Vector3f p2 = new Vector3f(a).add(side).add(other);
            Vector3f p3 = new Vector3f(b).add(side).add(other);
            Vector3f p4 = new Vector3f(b).add(side).sub(other);
            Vector3f n = sides[s];
            beamVertex(vc, pos, normal, p1, n, 0f, v0, r, g, bl, alpha);
            beamVertex(vc, pos, normal, p2, n, 1f, v0, r, g, bl, alpha);
            beamVertex(vc, pos, normal, p3, n, 1f, v1, r, g, bl, alpha);
            beamVertex(vc, pos, normal, p4, n, 0f, v1, r, g, bl, alpha);
        }
    }

    private static void beamVertex(VertexConsumer vc, Matrix4f pos, Matrix3f normal, Vector3f p, Vector3f n, float u, float v,
                                   float r, float g, float b, float a) {
        vc.vertex(pos, p.x(), p.y(), p.z()).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT).normal(normal, n.x(), n.y(), n.z()).next();
    }

    /** A flat annulus (or an arc of one) in the XZ plane at height {@code y}, centred on (cx, cz). */
    public static void ring(MatrixStack matrices, VertexConsumer vc, float cx, float y, float cz, float inner, float outer,
                            float startAngle, float sweep, int segments, float r, float g, float b, float alpha) {
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        for (int i = 0; i < segments; i++) {
            float a0 = startAngle + sweep * i / segments;
            float a1 = startAngle + sweep * (i + 1) / segments;
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            glowVertex(vc, pos, normal, cx + c0 * inner, y, cz + s0 * inner, r, g, b, alpha);
            glowVertex(vc, pos, normal, cx + c0 * outer, y, cz + s0 * outer, r, g, b, alpha);
            glowVertex(vc, pos, normal, cx + c1 * outer, y, cz + s1 * outer, r, g, b, alpha);
            glowVertex(vc, pos, normal, cx + c1 * inner, y, cz + s1 * inner, r, g, b, alpha);
        }
    }

    /** A four-pointed star of crossed quads, for impact flares and glints. */
    public static void flare(MatrixStack matrices, VertexConsumer vc, float size, float r, float g, float b, float alpha) {
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        float t = size * 0.18f;
        // horizontal arm, vertical arm, and the same pair rotated into the third axis
        float[][][] quads = {
                {{-size, -t, 0}, {size, -t, 0}, {size, t, 0}, {-size, t, 0}},
                {{-t, -size, 0}, {t, -size, 0}, {t, size, 0}, {-t, size, 0}},
                {{0, -t, -size}, {0, -t, size}, {0, t, size}, {0, t, -size}},
                {{0, -size, -t}, {0, -size, t}, {0, size, t}, {0, size, -t}}};
        for (float[][] q : quads) {
            for (float[] p : q) glowVertex(vc, pos, normal, p[0], p[1], p[2], r, g, b, alpha);
        }
    }

    public static void glowVertex(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(pos, x, y, z).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT).normal(normal, 0f, 1f, 0f).next();
    }
}
