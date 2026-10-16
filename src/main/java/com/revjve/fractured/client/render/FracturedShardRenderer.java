package com.revjve.fractured.client.render;

import com.revjve.fractured.registry.ModDimensions;
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
 * The Fractured's true form: a gaunt, two-metre skeleton of faceted smoky-glass crystals.
 *
 * <ul>
 *   <li>~95 octahedral shards hang off a 13-bone rig ({@link FracturedPose}): elongated arms
 *       ending in fans of needle claws, a crowned head, a ribcage of outward-splayed shards.</li>
 *   <li>Every facet is shaded by vanilla's entity lighting, tinted by a view-dependent fresnel rim
 *       (blood red at grazing angles) and flares with an emissive glint when it catches the light,
 *       so the body sparkles and bleeds red as it moves — even in total darkness.</li>
 *   <li>A heart of red light beats inside the ribcage; crackling veins arc from it to the limbs;
 *       two burning slits serve as eyes; a halo of shards orbits the shoulders.</li>
 *   <li>Mirror Crawl: after crossing a mirror each shard flies in from the glass behind on its own
 *       staggered delay while the body unfolds from a crawl.</li>
 * </ul>
 * Uses only vanilla textures (ice, white concrete) and vanilla entity shaders.
 */
@Environment(EnvType.CLIENT)
public final class FracturedShardRenderer {
    private static final Identifier GLASS = new Identifier("textures/block/ice.png");
    private static final Identifier SOLID = new Identifier("textures/block/white_concrete.png");

    private enum Bone {
        PELVIS, TORSO, HEAD, UPPER_ARM_R, FOREARM_R, CLAW_R, UPPER_ARM_L, FOREARM_L, CLAW_L, THIGH_R, SHIN_R, THIGH_L, SHIN_L, HALO
    }

    /** x, y, z, length, width, thickness, rx, ry, rz, phase, drift, delay, uvIndex */
    private static final float[][][] SHARDS = new float[Bone.values().length][][];

    private static final float TORSO_LEN = 0.66f, UPPER_ARM = 0.5f, FOREARM = 0.52f, THIGH = 0.48f, SHIN = 0.47f, PELVIS_Y = 0.95f;

    static {
        Random r = Random.create(0xF4AC7EDL);
        int[] uv = {0};
        SHARDS[Bone.PELVIS.ordinal()] = ring(r, uv, 7, 0f, 0.12f, 0.16f, 0.12f, 0.18f, 0.3f, 0.6f);
        SHARDS[Bone.TORSO.ordinal()] = concat(
                ring(r, uv, 8, 0.05f, 0.25f, 0.2f, 0.13f, 0.22f, 0.34f, 0.9f),
                ring(r, uv, 8, 0.28f, 0.5f, 0.24f, 0.15f, 0.2f, 0.32f, 1.1f),
                ring(r, uv, 6, 0.5f, 0.66f, 0.2f, 0.13f, 0.16f, 0.26f, 0.8f),
                spine(r, uv));
        SHARDS[Bone.HEAD.ordinal()] = concat(
                ring(r, uv, 9, 0.04f, 0.36f, 0.14f, 0.13f, 0.12f, 0.22f, 0.5f),
                crown(r, uv));
        for (Bone b : new Bone[]{Bone.UPPER_ARM_R, Bone.UPPER_ARM_L}) SHARDS[b.ordinal()] = limb(r, uv, 5, UPPER_ARM, 0.07f, 0.16f, 0.26f);
        for (Bone b : new Bone[]{Bone.FOREARM_R, Bone.FOREARM_L}) SHARDS[b.ordinal()] = limb(r, uv, 5, FOREARM, 0.06f, 0.16f, 0.28f);
        for (Bone b : new Bone[]{Bone.CLAW_R, Bone.CLAW_L}) SHARDS[b.ordinal()] = claws(r, uv);
        for (Bone b : new Bone[]{Bone.THIGH_R, Bone.THIGH_L}) SHARDS[b.ordinal()] = limb(r, uv, 5, THIGH, 0.08f, 0.18f, 0.28f);
        for (Bone b : new Bone[]{Bone.SHIN_R, Bone.SHIN_L}) SHARDS[b.ordinal()] = concat(limb(r, uv, 4, SHIN, 0.06f, 0.16f, 0.26f), foot(r, uv));
        SHARDS[Bone.HALO.ordinal()] = halo(r, uv);
    }

    private FracturedShardRenderer() {}

    // ------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------

    /**
     * Which layer the current traversal writes. The immediate vertex-consumer provider flushes the
     * previous layer whenever a different non-fixed layer is requested, so the skeleton is walked
     * once per layer instead of interleaving writes.
     */
    private enum Pass { GLASS, GLINT, EMISSIVE }

    /** Per-frame drawing context. */
    private static final class Ctx {
        final int light;
        final FracturedPose pose;
        final boolean mirror;
        final float alpha;
        Pass pass;
        VertexConsumer out;

        Ctx(int light, FracturedPose pose, boolean mirror, float alpha) {
            this.light = light;
            this.pose = pose;
            this.mirror = mirror;
            this.alpha = alpha;
        }

        void begin(VertexConsumerProvider consumers, Pass pass) {
            this.pass = pass;
            this.out = consumers.getBuffer(switch (pass) {
                case GLASS -> RenderLayer.getEntityTranslucent(GLASS);
                case GLINT -> RenderLayer.getEntityTranslucentEmissive(GLASS);
                case EMISSIVE -> RenderLayer.getEntityTranslucentEmissive(SOLID);
            });
        }
    }

    public static void render(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        FracturedPose pose = FracturedPose.compute(player, tickDelta);
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        boolean mirror = player.getWorld().getRegistryKey() == ModDimensions.MIRROR;
        Ctx ctx = new Ctx(light, pose, mirror, 0.82f);
        for (Pass pass : Pass.values()) {
            ctx.begin(consumers, pass);
            renderBody(matrices, ctx, bodyYaw);
        }
    }

    private static void renderBody(MatrixStack matrices, Ctx ctx, float bodyYaw) {
        FracturedPose pose = ctx.pose;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        matrices.translate(0f, pose.rootY, pose.rootZ);

        // pelvis
        matrices.push();
        matrices.translate(0f, PELVIS_Y, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(pose.twist * 0.4f));
        drawBone(matrices, ctx, Bone.PELVIS);

        // legs hang from the pelvis
        leg(matrices, ctx, -0.14f, pose.thighR, pose.kneeR, Bone.THIGH_R, Bone.SHIN_R);
        leg(matrices, ctx, 0.14f, pose.thighL, pose.kneeL, Bone.THIGH_L, Bone.SHIN_L);

        // torso, hunched forward
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(pose.twist));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-pose.lean));
        drawBone(matrices, ctx, Bone.TORSO);
        core(matrices, ctx);
        veins(matrices, ctx);
        halo(matrices, ctx);

        // head
        matrices.push();
        matrices.translate(0f, TORSO_LEN + 0.04f, -0.04f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.lean * 0.6f)); // keep the gaze level while hunched
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(pose.headYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.headPitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.headRoll));
        drawBone(matrices, ctx, Bone.HEAD);
        eyes(matrices, ctx);
        matrices.pop();

        // arms: long, hanging past the knees
        arm(matrices, ctx, -0.34f, pose.shoulderR, -pose.armRollR, pose.elbowR, pose.wristR, pose.clawSpreadR, Bone.UPPER_ARM_R, Bone.FOREARM_R, Bone.CLAW_R);
        arm(matrices, ctx, 0.34f, pose.shoulderL, pose.armRollL, pose.elbowL, pose.wristL, pose.clawSpreadL, Bone.UPPER_ARM_L, Bone.FOREARM_L, Bone.CLAW_L);
        matrices.pop(); // torso
        matrices.pop(); // pelvis
        matrices.pop(); // root
    }

    /** First-person view of The Fractured's own forearm and claws (replaces the vanilla arm). */
    public static void renderFirstPersonArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, boolean right) {
        FracturedPose pose = FracturedPose.compute(player, 1f);
        boolean mirror = player.getWorld().getRegistryKey() == ModDimensions.MIRROR;
        Ctx ctx = new Ctx(light, pose, mirror, 0.9f);
        for (Pass pass : new Pass[]{Pass.GLASS, Pass.GLINT}) {
            ctx.begin(consumers, pass);
            renderFirstPersonPass(matrices, ctx, right);
        }
    }

    private static void renderFirstPersonPass(MatrixStack matrices, Ctx ctx, boolean right) {
        FracturedPose pose = ctx.pose;
        matrices.push();
        // vanilla arm pivot, in model units (1/16 block, +Y down); flip so our bones extend toward the hand
        matrices.translate((right ? -5f : 5f) / 16f, 2f / 16f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.PI));
        matrices.translate(0f, 0.15f, 0f);
        drawBone(matrices, ctx, right ? Bone.FOREARM_R : Bone.FOREARM_L);
        matrices.translate(0f, -FOREARM, 0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(0.25f + FracturedPose.twitch(pose.time, right ? 3 : 7) * 0.3f));
        drawClaws(matrices, ctx, right ? Bone.CLAW_R : Bone.CLAW_L, 0.18f + MathHelper.sin(pose.time * 0.15f) * 0.06f);
        matrices.pop();
    }

    private static void leg(MatrixStack m, Ctx ctx, float x, float thigh, float knee, Bone thighBone, Bone shinBone) {
        m.push();
        m.translate(x, 0f, 0f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(thigh));
        drawBone(m, ctx, thighBone);
        m.translate(0f, -THIGH, 0f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(knee));
        drawBone(m, ctx, shinBone);
        m.pop();
    }

    private static void arm(MatrixStack m, Ctx ctx, float x, float shoulder, float roll, float elbow, float wrist, float spread,
                            Bone upper, Bone fore, Bone claw) {
        m.push();
        m.translate(x, TORSO_LEN - 0.08f, 0f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(shoulder));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
        drawBone(m, ctx, upper);
        m.translate(0f, -UPPER_ARM, 0f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(elbow));
        drawBone(m, ctx, fore);
        m.translate(0f, -FOREARM, 0f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(wrist));
        drawClaws(m, ctx, claw, spread);
        m.pop();
    }

    private static void drawClaws(MatrixStack m, Ctx ctx, Bone claw, float spread) {
        float[][] shards = SHARDS[claw.ordinal()];
        for (int n = 0; n < shards.length; n++) {
            m.push();
            float fan = (n - (shards.length - 1) * 0.5f) * spread;
            m.multiply(RotationAxis.POSITIVE_Z.rotation(fan));
            drawShard(m, ctx, shards[n], 1f);
            m.pop();
        }
    }

    private static void drawBone(MatrixStack m, Ctx ctx, Bone bone) {
        for (float[] s : SHARDS[bone.ordinal()]) drawShard(m, ctx, s, 1.08f);
    }

    /** One crystal, with idle drift ("disjointed" floating) and mirror-crawl assembly. */
    private static void drawShard(MatrixStack m, Ctx ctx, float[] s, float spreadOut) {
        FracturedPose pose = ctx.pose;
        float q = MathHelper.clamp((pose.assemble - s[11]) / 0.45f, 0f, 1f);
        if (q <= 0f) return;
        float arrive = easeOutBack(q);
        float drift = s[10] * MathHelper.sin(pose.time * 0.08f + s[9]);

        float x = s[0] * spreadOut, y = s[1], z = s[2] * spreadOut;
        float len = MathHelper.sqrt(x * x + z * z);
        if (len > 1.0e-4f) {
            x += x / len * drift;
            z += z / len * drift;
        }
        y += drift * 0.5f;
        // fly in from the glass behind (+Z) and from far away
        float from = 1f - arrive;
        x += from * (s[0] * 2.5f);
        y += from * 0.4f;
        z += from * (1.6f + s[9] * 0.3f);

        m.push();
        m.translate(x, y, z);
        m.multiply(RotationAxis.POSITIVE_Y.rotation(s[7] + MathHelper.sin(pose.time * 0.02f + s[9]) * 0.3f + from * 6f));
        m.multiply(RotationAxis.POSITIVE_X.rotation(s[6] + MathHelper.sin(pose.time * 0.05f + s[9]) * 0.12f));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(s[8]));
        float scale = 0.3f + 0.7f * q;
        m.scale(scale, scale, scale);
        crystal(m, ctx, s[3], s[4], s[5], (int) s[12], ctx.alpha * (0.4f + 0.6f * q));
        m.pop();
    }

    // ------------------------------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------------------------------

    private static final float[][] OCTA_FACES = {
            {0, 2, 3}, {0, 3, 4}, {0, 4, 5}, {0, 5, 2},
            {1, 3, 2}, {1, 4, 3}, {1, 5, 4}, {1, 2, 5}};

    /** An elongated octahedron (two pyramids base to base): the classic shard-of-glass crystal. */
    private static void crystal(MatrixStack m, Ctx ctx, float length, float width, float thickness, int uvIndex, float alpha) {
        if (ctx.pass == Pass.EMISSIVE) return;
        Matrix4f pos = m.peek().getPositionMatrix();
        Matrix3f normal = m.peek().getNormalMatrix();
        float hl = length * 0.5f, hw = width * 0.5f, ht = thickness * 0.5f;
        float[][] v = {{0, hl, 0}, {0, -hl, 0}, {hw, 0, 0}, {0, 0, ht}, {-hw, 0, 0}, {0, 0, -ht}};

        Vector4f center = new Vector4f(0f, 0f, 0f, 1f).mul(pos);
        Vector3f view = new Vector3f(-center.x(), -center.y(), -center.z());
        if (view.lengthSquared() < 1.0e-6f) view.set(0f, 0f, 1f);
        view.normalize();
        Vector3f lightDir = new Vector3f(view).add(0.45f, 0.75f, 0.1f).normalize();

        float u0 = (uvIndex & 3) * 0.25f, v0 = ((uvIndex >> 2) & 3) * 0.25f;
        for (float[] f : OCTA_FACES) {
            float[] a = v[(int) f[0]], b = v[(int) f[1]], c = v[(int) f[2]];
            Vector3f n = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2])
                    .cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
            if (n.lengthSquared() < 1.0e-10f) continue;
            n.normalize();
            Vector3f nv = new Vector3f(n).mul(normal).normalize();
            float facing = Math.abs(nv.dot(view));
            float rim = (float) Math.pow(1f - facing, 2.2);
            float spec = (float) Math.pow(Math.max(0f, nv.dot(lightDir)), 28.0);

            // smoky black glass; fresnel rim bleeds red; brighter, colder glass in the Main World
            float br = ctx.mirror ? 0.20f : 0.36f, bg = ctx.mirror ? 0.14f : 0.36f, bb = ctx.mirror ? 0.16f : 0.42f;
            float red = MathHelper.clamp(rim * 1.4f, 0f, 1f);
            float cr = MathHelper.lerp(red, br, 0.95f), cg = MathHelper.lerp(red, bg, 0.08f), cb = MathHelper.lerp(red, bb, 0.06f);
            if (ctx.pass == Pass.GLASS) {
                tri(ctx.out, pos, normal, a, b, c, n, u0, v0, cr, cg, cb, alpha, ctx.light);
            } else if (ctx.pass == Pass.GLINT && (spec > 0.04f || red > 0.55f)) {
                float g = Math.min(1f, spec * 1.6f);
                float glow = Math.max(g, (red - 0.55f) * 1.2f);
                tri(ctx.out, pos, normal, a, b, c, n, u0, v0, 1f, 0.25f + g * 0.75f, 0.2f + g * 0.75f, glow * alpha, 0xF000F0);
            }
        }
    }

    private static void tri(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float[] a, float[] b, float[] c, Vector3f n,
                            float u0, float v0, float r, float g, float bl, float alpha, int light) {
        vertex(vc, pos, normal, a, n, u0 + 0.125f, v0, r, g, bl, alpha, light);
        vertex(vc, pos, normal, b, n, u0, v0 + 0.25f, r, g, bl, alpha, light);
        vertex(vc, pos, normal, c, n, u0 + 0.25f, v0 + 0.25f, r, g, bl, alpha, light);
        vertex(vc, pos, normal, c, n, u0 + 0.25f, v0 + 0.25f, r, g, bl, alpha, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float[] p, Vector3f n, float u, float v,
                               float r, float g, float b, float a, int light) {
        vc.vertex(pos, p[0], p[1], p[2]).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(normal, n.x(), n.y(), n.z()).next();
    }

    /** The heart: nested, counter-rotating emissive crystals that throb with each beat. */
    private static void core(MatrixStack m, Ctx ctx) {
        if (ctx.pass != Pass.EMISSIVE) return;
        FracturedPose pose = ctx.pose;
        float beat = pose.corePulse;
        float a = ctx.alpha * pose.assemble;
        m.push();
        m.translate(0f, 0.36f, 0.0f);
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotation(pose.time * 0.06f));
        float s = 0.13f + beat * 0.05f;
        solidCrystal(m, ctx, s * 1.4f, s, s, 1f, 0.12f, 0.06f, 0.95f * a);
        m.pop();
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotation(-pose.time * 0.09f));
        m.multiply(RotationAxis.POSITIVE_X.rotation(0.6f));
        float shell = 0.28f + beat * 0.12f;
        solidCrystal(m, ctx, shell * 1.3f, shell, shell, 1f, 0.05f, 0.02f, (0.18f + beat * 0.25f) * a);
        m.pop();
        m.pop();
    }

    private static void solidCrystal(MatrixStack m, Ctx ctx, float length, float width, float thickness, float r, float g, float b, float alpha) {
        Matrix4f pos = m.peek().getPositionMatrix();
        Matrix3f normal = m.peek().getNormalMatrix();
        float hl = length * 0.5f, hw = width * 0.5f, ht = thickness * 0.5f;
        float[][] v = {{0, hl, 0}, {0, -hl, 0}, {hw, 0, 0}, {0, 0, ht}, {-hw, 0, 0}, {0, 0, -ht}};
        Vector3f up = new Vector3f(0f, 1f, 0f);
        for (float[] f : OCTA_FACES) {
            tri(ctx.out, pos, normal, v[(int) f[0]], v[(int) f[1]], v[(int) f[2]], up, 0f, 0f, r, g, b, alpha, 0xF000F0);
        }
    }

    /** Lightning-like veins arcing from the heart to the shoulders and hips, re-jittered every 2 ticks. */
    private static void veins(MatrixStack m, Ctx ctx) {
        if (ctx.pass != Pass.EMISSIVE) return;
        FracturedPose pose = ctx.pose;
        int seed = MathHelper.floor(pose.time / 2f);
        float[][] targets = {{-0.34f, TORSO_LEN - 0.08f, 0f}, {0.34f, TORSO_LEN - 0.08f, 0f}, {-0.14f, 0f, 0f}, {0.14f, 0f, 0f}, {0f, TORSO_LEN + 0.1f, -0.03f}};
        Matrix4f pos = m.peek().getPositionMatrix();
        Matrix3f normal = m.peek().getNormalMatrix();
        float flicker = 0.45f + 0.55f * FracturedPose.hash(seed * 13);
        for (int t = 0; t < targets.length; t++) {
            float[] prev = {0f, 0.36f, 0f};
            for (int seg = 1; seg <= 4; seg++) {
                float k = seg / 4f;
                float jitter = seg == 4 ? 0f : 0.05f;
                float[] next = {
                        MathHelper.lerp(k, 0f, targets[t][0]) + (FracturedPose.hash(seed * 7 + t * 31 + seg) - 0.5f) * jitter * 2f,
                        MathHelper.lerp(k, 0.36f, targets[t][1]) + (FracturedPose.hash(seed * 11 + t * 17 + seg) - 0.5f) * jitter * 2f,
                        MathHelper.lerp(k, 0f, targets[t][2]) + (FracturedPose.hash(seed * 5 + t * 23 + seg) - 0.5f) * jitter * 2f};
                ribbon(ctx.out, pos, normal, prev, next, 0.012f, 1f, 0.1f, 0.05f, 0.8f * flicker * ctx.alpha * pose.assemble);
                prev = next;
            }
        }
    }

    /** Two crossed quads along a segment, so a thin glowing line reads from every angle. */
    private static void ribbon(VertexConsumer vc, Matrix4f pos, Matrix3f normal, float[] a, float[] b, float w, float r, float g, float bl, float alpha) {
        Vector3f n = new Vector3f(0f, 1f, 0f);
        float[][] offsets = {{w, 0f, 0f}, {0f, 0f, w}};
        for (float[] o : offsets) {
            float[] p1 = {a[0] - o[0], a[1] - o[1], a[2] - o[2]};
            float[] p2 = {a[0] + o[0], a[1] + o[1], a[2] + o[2]};
            float[] p3 = {b[0] + o[0], b[1] + o[1], b[2] + o[2]};
            float[] p4 = {b[0] - o[0], b[1] - o[1], b[2] - o[2]};
            vertex(vc, pos, normal, p1, n, 0f, 0f, r, g, bl, alpha, 0xF000F0);
            vertex(vc, pos, normal, p2, n, 1f, 0f, r, g, bl, alpha, 0xF000F0);
            vertex(vc, pos, normal, p3, n, 1f, 1f, r, g, bl, alpha, 0xF000F0);
            vertex(vc, pos, normal, p4, n, 0f, 1f, r, g, bl, alpha, 0xF000F0);
        }
    }

    /** Two burning slits under the crown. They flare when the head twitches. */
    private static void eyes(MatrixStack m, Ctx ctx) {
        if (ctx.pass != Pass.EMISSIVE) return;
        FracturedPose pose = ctx.pose;
        float flare = Math.abs(FracturedPose.twitch(pose.time, 11));
        float alpha = (0.85f + flare * 0.15f) * pose.assemble * ctx.alpha;
        Matrix4f pos = m.peek().getPositionMatrix();
        Matrix3f normal = m.peek().getNormalMatrix();
        Vector3f n = new Vector3f(0f, 0f, -1f);
        float w = 0.05f + flare * 0.02f, h = 0.012f + flare * 0.01f;
        for (float side : new float[]{-0.065f, 0.065f}) {
            float cx = side, cy = 0.2f, cz = -0.165f;
            float[] l = {cx - w, cy + (side < 0 ? 0.01f : -0.01f), cz};
            float[] t = {cx, cy + h, cz};
            float[] rr = {cx + w, cy + (side < 0 ? -0.01f : 0.01f), cz};
            float[] b = {cx, cy - h, cz};
            vertex(ctx.out, pos, normal, l, n, 0f, 0f, 1f, 0.15f, 0.08f, alpha, 0xF000F0);
            vertex(ctx.out, pos, normal, b, n, 0f, 1f, 1f, 0.15f, 0.08f, alpha, 0xF000F0);
            vertex(ctx.out, pos, normal, rr, n, 1f, 1f, 1f, 0.15f, 0.08f, alpha, 0xF000F0);
            vertex(ctx.out, pos, normal, t, n, 1f, 0f, 1f, 0.15f, 0.08f, alpha, 0xF000F0);
        }
    }

    /** A slowly turning, tilted ring of small shards around the shoulders. */
    private static void halo(MatrixStack m, Ctx ctx) {
        FracturedPose pose = ctx.pose;
        float[][] shards = SHARDS[Bone.HALO.ordinal()];
        m.push();
        m.translate(0f, TORSO_LEN - 0.05f, 0.05f);
        m.multiply(RotationAxis.POSITIVE_X.rotation(0.35f + MathHelper.sin(pose.time * 0.03f) * 0.1f));
        m.multiply(RotationAxis.POSITIVE_Y.rotation(pose.time * 0.035f));
        for (int n = 0; n < shards.length; n++) {
            float angle = n * MathHelper.TAU / shards.length;
            float radius = 0.62f + MathHelper.sin(pose.time * 0.07f + n) * 0.04f;
            m.push();
            m.translate(MathHelper.cos(angle) * radius, MathHelper.sin(pose.time * 0.09f + n * 1.3f) * 0.05f, MathHelper.sin(angle) * radius);
            m.multiply(RotationAxis.POSITIVE_Y.rotation(-angle));
            m.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.HALF_PI));
            drawShard(m, ctx, shards[n], 1f);
            m.pop();
        }
        m.pop();
    }

    // ------------------------------------------------------------------------------------------
    // Shard layout generation (deterministic)
    // ------------------------------------------------------------------------------------------

    private static float[] shard(Random r, int[] uv, float x, float y, float z, float len, float wid, float thick, float rx, float ry, float rz) {
        float phase = r.nextFloat() * MathHelper.TAU;
        float drift = 0.012f + r.nextFloat() * 0.03f;
        float delay = r.nextFloat() * 0.55f;
        return new float[]{x, y, z, len, wid, thick, rx, ry, rz, phase, drift, delay, uv[0]++ & 15};
    }

    /** A ring of outward-splayed shards between y0 and y1 (ribcage, pelvis, skull). */
    private static float[][] ring(Random r, int[] uv, int count, float y0, float y1, float rx, float rz, float minLen, float maxLen, float splay) {
        float[][] out = new float[count][];
        for (int n = 0; n < count; n++) {
            float a = n * MathHelper.TAU / count + r.nextFloat() * 0.4f;
            float y = MathHelper.lerp(r.nextFloat(), y0, y1);
            float len = MathHelper.lerp(r.nextFloat(), minLen, maxLen);
            out[n] = shard(r, uv, MathHelper.cos(a) * rx, y, MathHelper.sin(a) * rz, len, len * 0.32f, len * 0.16f,
                    (r.nextFloat() - 0.5f) * splay, -a, (r.nextFloat() - 0.5f) * splay);
        }
        return out;
    }

    private static float[][] spine(Random r, int[] uv) {
        float[][] out = new float[5][];
        for (int n = 0; n < 5; n++) {
            float y = 0.05f + n * 0.14f;
            out[n] = shard(r, uv, 0f, y, 0.15f, 0.16f, 0.07f, 0.05f, 0.9f + r.nextFloat() * 0.3f, 0f, 0f);
        }
        return out;
    }

    /** Long spikes sweeping up and back from the skull. */
    private static float[][] crown(Random r, int[] uv) {
        float[][] out = new float[5][];
        for (int n = 0; n < 5; n++) {
            float x = (n - 2) * 0.07f;
            float len = 0.3f + r.nextFloat() * 0.18f - Math.abs(n - 2) * 0.05f;
            out[n] = shard(r, uv, x, 0.38f, 0.04f, len, 0.06f, 0.035f, 0.55f + r.nextFloat() * 0.2f, 0f, -x * 2.2f);
        }
        return out;
    }

    /** Shards strung along a bone that hangs down its local -Y axis. */
    private static float[][] limb(Random r, int[] uv, int count, float boneLen, float radius, float minLen, float maxLen) {
        float[][] out = new float[count][];
        for (int n = 0; n < count; n++) {
            float k = (n + 0.5f) / count;
            float a = r.nextFloat() * MathHelper.TAU;
            float len = MathHelper.lerp(r.nextFloat(), minLen, maxLen);
            out[n] = shard(r, uv, MathHelper.cos(a) * radius, -k * boneLen, MathHelper.sin(a) * radius, len, len * 0.3f, len * 0.16f,
                    (r.nextFloat() - 0.5f) * 0.5f, a, (r.nextFloat() - 0.5f) * 0.5f);
        }
        return out;
    }

    /** A fan of four needle claws. */
    private static float[][] claws(Random r, int[] uv) {
        float[][] out = new float[4][];
        for (int n = 0; n < 4; n++) {
            float len = 0.36f + r.nextFloat() * 0.14f;
            out[n] = shard(r, uv, 0f, -len * 0.5f, -0.02f, len, 0.045f, 0.03f, -0.15f, 0f, 0f);
        }
        return out;
    }

    private static float[][] foot(Random r, int[] uv) {
        return new float[][]{shard(r, uv, 0f, -SHIN, -0.08f, 0.26f, 0.08f, 0.05f, MathHelper.HALF_PI - 0.2f, 0f, 0f)};
    }

    private static float[][] halo(Random r, int[] uv) {
        float[][] out = new float[12][];
        for (int n = 0; n < 12; n++) {
            float len = 0.1f + r.nextFloat() * 0.08f;
            out[n] = shard(r, uv, 0f, 0f, 0f, len, len * 0.35f, len * 0.2f, 0f, 0f, 0f);
        }
        return out;
    }

    private static float[][] concat(float[][]... parts) {
        int total = 0;
        for (float[][] p : parts) total += p.length;
        float[][] out = new float[total][];
        int i = 0;
        for (float[][] p : parts) for (float[] s : p) out[i++] = s;
        return out;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }
}
