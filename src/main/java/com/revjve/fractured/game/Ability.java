package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;

import java.util.function.ToIntFunction;

/** The Fractured's active abilities. Entity Phasing is passive and has no entry here. */
public enum Ability {
    PORTAL(cfg -> cfg.portalCooldownSeconds),
    DRAG(cfg -> cfg.dragCooldownSeconds),
    SHAPESHIFT(cfg -> cfg.shapeshiftCooldownSeconds),
    GLASS_ECHO(cfg -> cfg.echoCooldownSeconds),
    BLACKOUT(cfg -> cfg.blackoutCooldownSeconds);

    private final ToIntFunction<FRConfig> cooldownSeconds;

    Ability(ToIntFunction<FRConfig> cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int cooldownTicks() {
        return Math.max(0, cooldownSeconds.applyAsInt(FRConfig.get())) * 20;
    }

    public String translationKey() {
        return "ability.fractured_reflections." + name().toLowerCase();
    }

    public static Ability byOrdinal(int ordinal) {
        Ability[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
