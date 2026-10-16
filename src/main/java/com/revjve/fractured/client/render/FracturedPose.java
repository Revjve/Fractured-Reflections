package com.revjve.fractured.client.render;

import com.revjve.fractured.client.ClientGameState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Procedural animation rig for The Fractured, rebuilt every frame from the player's motion.
 * All angles are radians; positive limb pitch swings a limb forward (the model faces -Z).
 *
 * <p>Layers, blended in order: breathing idle → walk/sprint cycle → sneaking crouch → flight
 * pose → claw slash (hand swing) → mirror crawl (emerging out of glass) → head twitches.
 */
@Environment(EnvType.CLIENT)
final class FracturedPose {
    // root
    float rootY, rootZ, lean, twist, bodyRoll;
    // head
    float headYaw, headPitch, headRoll;
    // arms (R = model right = -X)
    float shoulderR, shoulderL, armRollR, armRollL, elbowR, elbowL, wristR, wristL, clawSpreadR, clawSpreadL;
    // legs
    float thighR, thighL, kneeR, kneeL;
    // effects
    float corePulse, crawl, assemble, time;
    boolean flying;

    static FracturedPose compute(PlayerEntity player, float tickDelta) {
        FracturedPose p = new FracturedPose();
        float t = player.age + tickDelta;
        p.time = t;
        float limbPos = player.limbAnimator.getPos(tickDelta);
        float speed = Math.min(1f, player.limbAnimator.getSpeed(tickDelta));
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        boolean sprinting = player.isSprinting();
        p.flying = player.getAbilities().flying && !player.isOnGround();

        // --- idle: slow breathing, swaying claws -------------------------------------------
        float breath = MathHelper.sin(t * 0.07f);
        p.rootY = breath * 0.025f;
        p.lean = 0.16f + breath * 0.02f;
        p.shoulderR = 0.08f + MathHelper.sin(t * 0.045f) * 0.06f;
        p.shoulderL = 0.08f + MathHelper.sin(t * 0.045f + 1.7f) * 0.06f;
        p.armRollR = 0.10f;
        p.armRollL = 0.10f;
        p.elbowR = 0.30f + MathHelper.sin(t * 0.06f) * 0.05f;
        p.elbowL = 0.30f + MathHelper.sin(t * 0.06f + 2.1f) * 0.05f;
        p.wristR = 0.20f + twitch(t, 3) * 0.5f;
        p.wristL = 0.20f + twitch(t, 7) * 0.5f;
        p.clawSpreadR = 0.12f + MathHelper.sin(t * 0.11f) * 0.04f;
        p.clawSpreadL = 0.12f + MathHelper.sin(t * 0.11f + 1.0f) * 0.04f;
        p.kneeR = p.kneeL = -0.08f;

        // --- walk / sprint ---------------------------------------------------------------------
        float phase = limbPos * 0.6662f;
        float stride = speed * (sprinting ? 1.0f : 0.75f);
        float s = MathHelper.sin(phase);
        p.thighR = s * 0.8f * stride;
        p.thighL = -s * 0.8f * stride;
        p.kneeR -= Math.max(0f, -s) * 1.1f * stride;
        p.kneeL -= Math.max(0f, s) * 1.1f * stride;
        p.shoulderR += -s * 0.55f * stride;
        p.shoulderL += s * 0.55f * stride;
        p.elbowR += 0.35f * stride;
        p.elbowL += 0.35f * stride;
        p.rootY += Math.abs(MathHelper.cos(phase)) * 0.06f * stride;
        p.lean += stride * (sprinting ? 0.55f : 0.25f);
        p.twist = s * 0.12f * stride;

        // --- crouch ---------------------------------------------------------------------------
        if (player.isInSneakingPose()) {
            p.rootY -= 0.22f;
            p.lean += 0.35f;
            p.thighR += 0.55f;
            p.thighL += 0.55f;
            p.kneeR -= 0.75f;
            p.kneeL -= 0.75f;
            p.shoulderR += 0.4f;
            p.shoulderL += 0.4f;
        }

        // --- flight: dangling, trailing limbs, arms flared ------------------------------------
        if (p.flying) {
            float sway = MathHelper.sin(t * 0.09f);
            p.rootY += 0.1f + sway * 0.04f;
            p.lean += 0.30f;
            p.thighR = -0.35f + sway * 0.1f;
            p.thighL = -0.25f - sway * 0.1f;
            p.kneeR = -0.8f;
            p.kneeL = -0.7f;
            p.shoulderR = 0.45f + sway * 0.08f;
            p.shoulderL = 0.45f - sway * 0.08f;
            p.armRollR = 0.65f;
            p.armRollL = 0.65f;
            p.clawSpreadR = p.clawSpreadL = 0.35f;
        }

        // --- claw slash (attack swing) -------------------------------------------------------
        float swing = player.getHandSwingProgress(tickDelta);
        if (swing > 0f) {
            float raise = swing < 0.3f ? swing / 0.3f : 1f - easeOut((swing - 0.3f) / 0.7f);
            float slash = swing < 0.3f ? 0f : easeOut((swing - 0.3f) / 0.7f);
            p.shoulderR = MathHelper.lerp(raise, p.shoulderR, 2.9f) - slash * 0.6f;
            p.armRollR += raise * 0.35f;
            p.elbowR = MathHelper.lerp(raise, p.elbowR, 0.15f);
            p.wristR = 0.6f;
            p.clawSpreadR = 0.45f;
            p.twist += MathHelper.sin(swing * MathHelper.PI) * 0.45f;
            p.lean += slash * 0.25f;
        }

        // --- head: follow look direction, plus horror twitches -------------------------------
        p.headYaw = (bodyYaw - headYaw) * MathHelper.RADIANS_PER_DEGREE;
        p.headPitch = -pitch * MathHelper.RADIANS_PER_DEGREE;
        float jerk = twitch(t, 11);
        p.headYaw += jerk * 0.5f;
        p.headRoll = twitch(t, 17) * 0.6f + MathHelper.sin(t * 0.03f) * 0.06f;

        // --- core heartbeat --------------------------------------------------------------------
        float beat = MathHelper.sin(t * 0.21f) * 0.5f + 0.5f;
        p.corePulse = beat * beat * beat * beat;

        // --- mirror crawl: unfolding out of the glass behind ----------------------------------
        ClientGameState.Crawl crawl = ClientGameState.crawls.get(player.getId());
        float progress = crawl == null ? 1f
                : MathHelper.clamp((ClientGameState.clientTicks - crawl.startedAt() + tickDelta) / Math.max(1, crawl.duration()), 0f, 1f);
        p.assemble = progress;
        float e = 1f - progress;
        p.crawl = e * e * (3f - 2f * e);
        if (p.crawl > 0f) {
            p.rootZ = p.crawl * 0.9f;
            p.rootY -= p.crawl * 0.75f;
            p.lean += p.crawl * 1.15f;
            p.shoulderR = MathHelper.lerp(p.crawl, p.shoulderR, 1.9f + MathHelper.sin(t * 0.5f) * 0.3f);
            p.shoulderL = MathHelper.lerp(p.crawl, p.shoulderL, 1.9f - MathHelper.sin(t * 0.5f) * 0.3f);
            p.elbowR = MathHelper.lerp(p.crawl, p.elbowR, 0.1f);
            p.elbowL = MathHelper.lerp(p.crawl, p.elbowL, 0.1f);
            p.clawSpreadR = p.clawSpreadL = 0.5f;
            p.thighR = MathHelper.lerp(p.crawl, p.thighR, 1.2f);
            p.thighL = MathHelper.lerp(p.crawl, p.thighL, 1.0f);
            p.kneeR = MathHelper.lerp(p.crawl, p.kneeR, -1.6f);
            p.kneeL = MathHelper.lerp(p.crawl, p.kneeL, -1.5f);
            p.headPitch = MathHelper.lerp(p.crawl, p.headPitch, 0.9f); // looking up at its prey
        }
        return p;
    }

    /**
     * A sparse, sudden, deterministic jerk in [-1, 1]: mostly 0, occasionally snapping to a
     * random angle for a few ticks. {@code salt} decorrelates different joints.
     */
    static float twitch(float time, int salt) {
        int window = MathHelper.floor(time / 23f);
        float h = hash(window * 31 + salt);
        float local = time - window * 23f;
        if (h < 0.55f || local > 4f) return 0f;
        float shape = local < 1f ? local : 1f - (local - 1f) / 3f;
        return (hash(window * 17 + salt * 7) * 2f - 1f) * shape;
    }

    static float hash(int n) {
        n = (n << 13) ^ n;
        int m = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return m / (float) 0x7fffffff;
    }

    private static float easeOut(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
