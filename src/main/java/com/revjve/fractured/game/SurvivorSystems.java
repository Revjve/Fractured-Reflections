package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.item.FlashlightItem;
import com.revjve.fractured.registry.ModDimensions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;

import java.util.UUID;

/** Server-authoritative stamina and sanity simulation for survivors. */
public final class SurvivorSystems {
    public static final UUID STAMINA_MODIFIER = UUID.fromString("6f7d4b52-7c0b-4c64-9a8e-2a6b1df0c901");
    private static final double FRACTURED_SIGHT_RANGE = 16.0;
    /** Ever-present dread of the Mirror Dimension, before the x2 multiplier. */
    private static final float MIRROR_BASE_DRAIN = 0.4f;

    private SurvivorSystems() {}

    public static void tick(Match match, ServerPlayerEntity player, PlayerState state) {
        FRConfig cfg = FRConfig.get();
        tickStamina(player, state, cfg);
        tickSanity(match, player, state, cfg);
    }

    // --- Stamina -----------------------------------------------------------------------------

    private static void tickStamina(ServerPlayerEntity player, PlayerState state, FRConfig cfg) {
        boolean sprinting = player.isSprinting();
        if (sprinting && !state.exhausted) {
            state.stamina -= cfg.staminaDrainPerTick;
        } else {
            boolean resting = player.getVelocity().horizontalLengthSquared() < 1.0E-3;
            state.stamina += cfg.staminaRegenPerTick * (resting ? 1.6f : 1.0f);
        }
        state.stamina = MathHelper.clamp(state.stamina, 0f, cfg.staminaMax);

        if (state.stamina <= 0f && !state.exhausted) {
            state.exhausted = true;
            player.setSprinting(false);
        } else if (state.exhausted && state.stamina >= cfg.staminaRecoverThreshold) {
            state.exhausted = false;
        }
        if (state.exhausted && sprinting) player.setSprinting(false); // never trust the client

        // Sprint speed fades as stamina drains; exhaustion leaves the legs heavy.
        double bonus;
        if (state.exhausted) bonus = -0.15;
        else if (sprinting) bonus = -0.25 * (1.0 - state.stamina / cfg.staminaMax);
        else bonus = 0.0;
        bonus = Math.round(bonus * 20.0) / 20.0; // quantise to avoid attribute packet spam
        applySpeedModifier(player, bonus);
    }

    public static void applySpeedModifier(ServerPlayerEntity player, double amount) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        EntityAttributeModifier current = speed.getModifier(STAMINA_MODIFIER);
        if (current != null && current.getValue() == amount) return;
        if (current != null) speed.removeModifier(STAMINA_MODIFIER);
        if (amount != 0.0) {
            speed.addTemporaryModifier(new EntityAttributeModifier(STAMINA_MODIFIER, "fractured_reflections:stamina", amount,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // --- Sanity ------------------------------------------------------------------------------

    private static void tickSanity(Match match, ServerPlayerEntity player, PlayerState state, FRConfig cfg) {
        boolean inMirror = ModDimensions.isMirror(player.getWorld());
        int light = player.getWorld().getLightLevel(LightType.BLOCK, player.getBlockPos().up());
        boolean flashlight = FlashlightItem.isLitInHand(player);
        boolean fracturedInSight = fracturedInSight(match, player);

        float drain = 0f;
        if (light < 5) drain += cfg.sanityDarknessDrainPerSecond * (flashlight ? 0.4f : 1.0f);
        if (fracturedInSight) drain += cfg.sanityFracturedSightDrainPerSecond;
        if (inMirror) drain = (drain + MIRROR_BASE_DRAIN) * cfg.sanityMirrorMultiplier;

        float regen = !inMirror && !fracturedInSight && light >= 10 ? cfg.sanityRegenPerSecond : 0f;
        state.sanity = MathHelper.clamp(state.sanity + (regen - drain) / 20f, 0f, cfg.sanityMax);

        if (state.sanity <= 0f && cfg.sanityZeroEliminates) {
            match.eliminate(player, "consumed");
        }
    }

    private static boolean fracturedInSight(Match match, ServerPlayerEntity player) {
        for (PlayerState s : match.states()) {
            if (!s.isFractured() || s.disguiseTarget != null) continue; // a disguise hides the horror
            ServerPlayerEntity fractured = match.player(s.uuid);
            if (fractured == null || fractured.getWorld() != player.getWorld()) continue;
            if (fractured.squaredDistanceTo(player) <= FRACTURED_SIGHT_RANGE * FRACTURED_SIGHT_RANGE && player.canSee(fractured)) {
                return true;
            }
        }
        return false;
    }

    /** Instant sanity loss (witnessing a blackout, a teammate's death...). */
    public static void shock(PlayerState state, float amount) {
        state.sanity = Math.max(0f, state.sanity - amount);
    }
}
