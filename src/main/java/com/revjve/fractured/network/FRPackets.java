package com.revjve.fractured.network;

import com.revjve.fractured.FracturedReflections;
import net.minecraft.util.Identifier;

/** Channel identifiers. All payloads are hand-written PacketByteBufs (see ServerNetworking / ClientNetworking). */
public final class FRPackets {
    // Server -> client
    public static final Identifier ROLE_SYNC = FracturedReflections.id("role_sync");
    public static final Identifier SURVIVOR_STATS = FracturedReflections.id("survivor_stats");
    public static final Identifier FRACTURED_HUD = FracturedReflections.id("fractured_hud");
    public static final Identifier TASKS = FracturedReflections.id("tasks");
    public static final Identifier DISGUISES = FracturedReflections.id("disguises");
    public static final Identifier GHOST_ECHOES = FracturedReflections.id("ghost_echoes");
    public static final Identifier MIRROR_CRAWL = FracturedReflections.id("mirror_crawl");
    public static final Identifier ALTAR_REVEAL = FracturedReflections.id("altar_reveal");
    public static final Identifier SCREEN_EFFECT = FracturedReflections.id("screen_effect");
    public static final Identifier OPEN_PUZZLE = FracturedReflections.id("open_puzzle");

    // Client -> server
    public static final Identifier USE_ABILITY = FracturedReflections.id("use_ability");
    public static final Identifier TOGGLE_FLASHLIGHT = FracturedReflections.id("toggle_flashlight");
    public static final Identifier PUZZLE_SUBMIT = FracturedReflections.id("puzzle_submit");

    /** One-shot full-screen effects driven by the composite post shader and HUD. */
    public enum ScreenEffect {
        BLACKOUT_FLASH, DRAGGED, ESCAPED, STING, BANISHMENT, SHATTERED;

        public static ScreenEffect byOrdinal(int ordinal) {
            ScreenEffect[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STING;
        }
    }

    private FRPackets() {}
}
