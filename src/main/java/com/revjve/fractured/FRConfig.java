package com.revjve.fractured;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-authoritative tuning values. Loaded from {@code config/fractured_reflections.json};
 * missing keys fall back to the defaults below and the file is rewritten with every key.
 */
public final class FRConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FRConfig instance = new FRConfig();

    // --- Match ---------------------------------------------------------------------------
    /** Minimum players for /fr start (1 = solo test: you become the only survivor and no killer is assigned). */
    public int minPlayers = 2;
    public int maxSurvivors = 4;
    public int prepareSeconds = 15;
    public int matchMinutes = 15;
    public int endScreenSeconds = 12;
    /** Survivors play in survival instead of adventure (allows breaking/placing — still mirrored). */
    public boolean allowBlockEditing = false;
    public boolean sanityZeroEliminates = true;
    public float fracturedAttackDamage = 7.0f;

    // --- Mansion -------------------------------------------------------------------------
    public int mansionWidth = 5;
    public int mansionDepth = 5;
    public int mansionFloors = 3;
    public int mansionOriginY = 80;
    public int blocksPerTick = 12000;
    public int puzzleChests = 6;
    public int fragmentsPerAltar = 3;
    public int spareFragments = 2;

    // --- Survivors -----------------------------------------------------------------------
    public float staminaMax = 100f;
    public float staminaDrainPerTick = 0.55f;
    public float staminaRegenPerTick = 0.35f;
    /** Once drained to zero, sprinting is locked until stamina recovers to this value. */
    public float staminaRecoverThreshold = 35f;
    public float sanityMax = 100f;
    public float sanityDarknessDrainPerSecond = 0.35f;
    public float sanityFracturedSightDrainPerSecond = 1.5f;
    public float sanityRegenPerSecond = 0.3f;
    public float sanityMirrorMultiplier = 2.0f;
    public int flashlightBatteryTicks = 20 * 150;
    public int batteryRechargeTicks = 20 * 90;
    public int proximityChatRange = 24;

    // --- The Fractured -------------------------------------------------------------------
    public int portalCooldownSeconds = 40;
    public int portalDurationSeconds = 20;
    public int dragCooldownSeconds = 60;
    public double dragRange = 3.5;
    public int dragWindupTicks = 25;
    public int shapeshiftCooldownSeconds = 45;
    public int shapeshiftDurationSeconds = 60;
    public double shapeshiftRange = 24;
    public int echoCooldownSeconds = 30;
    public int echoLifetimeSeconds = 20;
    public int blackoutCooldownSeconds = 90;
    public int blackoutRadius = 30;
    /** Shattered lights reform after this many seconds (0 = never). */
    public int blackoutRestoreSeconds = 75;
    public int phaseCooldownTicks = 30;
    public float fracturedFlySpeed = 0.035f;

    // --- Rituals -------------------------------------------------------------------------
    public int cleansingSeconds = 60;
    public double cleansingRadius = 3.5;
    public int cleansingCueIntervalTicks = 40;

    // --- Integrations --------------------------------------------------------------------
    /** Spawn Immersive Portals Mirror entities on every mirror block for true planar reflections (GPU heavy). */
    public boolean immersivePortalsReflections = false;
    /** Undisguised Fractured voice is not delivered to survivors (Simple Voice Chat). */
    public boolean silenceUndisguisedFractured = true;
    /** Trapped survivors' voices leak faintly into the Main World at the same coordinates. */
    public boolean mirrorVoiceLeak = true;

    public static FRConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FracturedReflections.MOD_ID + ".json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                FRConfig loaded = GSON.fromJson(reader, FRConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                FracturedReflections.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            FracturedReflections.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
