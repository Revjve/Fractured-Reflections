package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModParticles {
    /** Slow-drifting glass dust that fills the air of the Mirror Dimension. */
    public static final DefaultParticleType GLASS_MOTE = Registry.register(Registries.PARTICLE_TYPE,
            FracturedReflections.id("glass_mote"), FabricParticleTypes.simple(true));

    private ModParticles() {}

    public static void register() {
        // class-load trigger
    }
}
