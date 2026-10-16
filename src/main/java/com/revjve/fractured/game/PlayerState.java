package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Server-side per-participant match state. */
public final class PlayerState {
    public final UUID uuid;
    public final String name;
    public Role role;

    // Survivor meters
    public float stamina;
    public float sanity;
    public boolean exhausted;
    public boolean eliminated;
    public int invulnerableTicks;

    // The Fractured
    public final int[] cooldowns = new int[Ability.values().length];
    public int phaseCooldown;
    @Nullable public UUID disguiseTarget;
    public int disguiseTicks;
    @Nullable public UUID dragTarget;
    public int dragWindup;

    // Bookkeeping
    @Nullable public RegistryKey<World> lastWorld;
    public float sentStamina = -1;
    public float sentSanity = -1;
    public boolean sentTrapped;
    public boolean sentExhausted;
    public boolean panicking;

    public PlayerState(UUID uuid, String name, Role role) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.stamina = FRConfig.get().staminaMax;
        this.sanity = FRConfig.get().sanityMax;
    }

    public boolean isActiveSurvivor() {
        return role == Role.SURVIVOR && !eliminated;
    }

    public boolean isFractured() {
        return role == Role.FRACTURED;
    }

    public boolean onCooldown(Ability ability) {
        return cooldowns[ability.ordinal()] > 0;
    }

    public void startCooldown(Ability ability) {
        cooldowns[ability.ordinal()] = ability.cooldownTicks();
    }
}
