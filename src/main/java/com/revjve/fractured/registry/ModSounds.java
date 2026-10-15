package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Every sound event is declared here and mapped in {@code assets/fractured_reflections/sounds.json}
 * onto premade audio (vanilla sound events by default). Swap in a licensed SFX pack by pointing the
 * JSON entries at .ogg files under {@code assets/fractured_reflections/sounds/} — no code changes.
 */
public final class ModSounds {
    public static final SoundEvent HEARTBEAT = register("heartbeat");
    public static final SoundEvent STING = register("sting");
    public static final SoundEvent GLASS_SHATTER = register("glass_shatter");
    public static final SoundEvent WHISPER = register("whisper");
    public static final SoundEvent FAKE_FOOTSTEP = register("fake_footstep");
    public static final SoundEvent MIRROR_AMBIENT = register("mirror_ambient");
    public static final SoundEvent MIRROR_PHASE = register("mirror_phase");
    public static final SoundEvent MIRROR_CRAWL = register("mirror_crawl");
    public static final SoundEvent PORTAL_OPEN = register("portal_open");
    public static final SoundEvent PORTAL_CLOSE = register("portal_close");
    public static final SoundEvent DIMENSION_DRAG = register("dimension_drag");
    public static final SoundEvent ESCAPE = register("escape");
    public static final SoundEvent SHAPESHIFT = register("shapeshift");
    public static final SoundEvent ECHO_SPAWN = register("echo_spawn");
    public static final SoundEvent ECHO_SHATTER = register("echo_shatter");
    public static final SoundEvent BLACKOUT = register("blackout");
    public static final SoundEvent LIGHTS_REFORM = register("lights_reform");
    public static final SoundEvent FLASHLIGHT_CLICK = register("flashlight_click");
    public static final SoundEvent FLASHLIGHT_FLICKER = register("flashlight_flicker");
    public static final SoundEvent LENS_ROTATE = register("lens_rotate");
    public static final SoundEvent REFRACTION_LIT = register("refraction_lit");
    public static final SoundEvent PURE_MIRROR_SEATED = register("pure_mirror_seated");
    public static final SoundEvent CLEANSING_CUE = register("cleansing_cue");
    public static final SoundEvent ALTAR_CLEANSED = register("altar_cleansed");
    public static final SoundEvent BANISHMENT = register("banishment");
    public static final SoundEvent PUZZLE_FAIL = register("puzzle_fail");
    public static final SoundEvent PUZZLE_SOLVED = register("puzzle_solved");
    public static final SoundEvent MATCH_START = register("match_start");

    private ModSounds() {}

    private static SoundEvent register(String name) {
        Identifier id = FracturedReflections.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void register() {
        // class-load trigger
    }
}
