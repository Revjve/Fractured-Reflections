package com.revjve.fractured.client;

import com.revjve.fractured.game.Ability;
import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.game.Role;
import com.revjve.fractured.game.RoleRegistry;
import com.revjve.fractured.registry.ModDimensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the client knows about the running match, filled by ClientNetworking. */
@Environment(EnvType.CLIENT)
public final class ClientGameState {
    public static MatchPhase phase = MatchPhase.IDLE;
    public static int phaseTicks;
    public static final Map<UUID, Role> roles = new HashMap<>();
    public static final Map<UUID, Boolean> eliminated = new HashMap<>();
    /** fractured uuid -> disguise target uuid */
    public static final Map<UUID, UUID> disguises = new HashMap<>();
    /** Survivors whose sanity is breaking — visible to The Fractured through walls (Glass Sense). */
    public static final java.util.Set<UUID> panicking = new java.util.HashSet<>();

    // survivor HUD
    public static float stamina = 100, staminaMax = 100, sanity = 100, sanityMax = 100;
    public static boolean exhausted;
    public static boolean trapped;

    // fractured HUD
    public static final int[] cooldowns = new int[Ability.values().length];
    public static final int[] cooldownMax = new int[Ability.values().length];
    public static int disguiseTicks;
    public static String disguiseName = "";
    public static int dragWindup;

    // tasks
    public record AltarTask(boolean refracted, boolean forged, boolean cleansing, boolean completed, float progress) {}
    public static final List<AltarTask> altars = new ArrayList<>();
    public static int fragmentsPerAltar = 3;

    // transient effects
    public record Reveal(BlockPos pos, int expiresAt) {}
    public static final List<Reveal> reveals = new ArrayList<>();
    public record Crawl(Direction facing, int startedAt, int duration) {}
    public static final Map<Integer, Crawl> crawls = new HashMap<>();
    public static float screenPulse;
    public static float dragPulse;
    /** Shattered-glass screen overlay strength from one-shot events (decays). */
    public static float crackPulse;
    /** Camera shake strength (decays); see CameraShakeMixin. */
    public static float shake;
    /** Red edge flash on each heartbeat (decays). */
    public static float heartbeatFlash;
    public static int clientTicks;

    /** 0..1 proximity of The Fractured to the local player (1 = touching), ignoring disguised killers. */
    public static float fracturedProximity;

    private ClientGameState() {}

    public static boolean inMatch() {
        return phase != MatchPhase.IDLE;
    }

    public static Role localRole() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return Role.NONE;
        return roles.getOrDefault(client.player.getUuid(), Role.NONE);
    }

    public static boolean isLocalSurvivor() {
        return localRole() == Role.SURVIVOR;
    }

    public static boolean isLocalFractured() {
        return localRole() == Role.FRACTURED;
    }

    @Nullable
    public static UUID disguiseOf(UUID fractured) {
        return disguises.get(fractured);
    }

    public static boolean inMirrorDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.world.getRegistryKey() == ModDimensions.MIRROR;
    }

    /**
     * Flashlight output 0..1 for an entity. Flickers violently when The Fractured is near the
     * local player; other players' lights flicker by the same signal (shared dread).
     */
    public static float flashlightIntensity(PlayerEntity holder) {
        float p = fracturedProximity;
        if (p <= 0.05f) return 1.0f;
        long t = clientTicks + holder.getId() * 7L;
        // layered pseudo-random stutter that gets harsher as the killer approaches
        float noise = MathHelper.sin(t * 1.7f) * MathHelper.sin(t * 0.43f + 1.3f) + MathHelper.sin(t * 3.1f) * 0.5f;
        boolean cut = noise > 1.0f - p * 1.6f;
        return cut ? 0.1f : 1.0f - p * 0.25f;
    }

    public static void updateProximity(MinecraftClient client) {
        fracturedProximity = 0;
        if (client.player == null || client.world == null || !isLocalSurvivor()) return;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || !RoleRegistry.isFracturedClient(p.getUuid())) continue;
            if (disguises.containsKey(p.getUuid())) continue;
            double d = Math.sqrt(p.squaredDistanceTo(client.player));
            fracturedProximity = Math.max(fracturedProximity, (float) MathHelper.clamp(1.0 - d / 16.0, 0.0, 1.0));
        }
    }

    public static void reset() {
        phase = MatchPhase.IDLE;
        phaseTicks = 0;
        roles.clear();
        eliminated.clear();
        disguises.clear();
        panicking.clear();
        altars.clear();
        reveals.clear();
        crawls.clear();
        stamina = staminaMax = sanity = sanityMax = 100;
        exhausted = trapped = false;
        disguiseTicks = dragWindup = 0;
        disguiseName = "";
        screenPulse = dragPulse = crackPulse = shake = heartbeatFlash = 0;
        fracturedProximity = 0;
        RoleRegistry.setClient(List.of());
        RoleRegistry.setClientHiding(List.of());
    }
}
