package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.entity.GlassEchoEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<GlassEchoEntity> GLASS_ECHO = Registry.register(Registries.ENTITY_TYPE,
            FracturedReflections.id("glass_echo"),
            FabricEntityTypeBuilder.<GlassEchoEntity>create(SpawnGroup.MISC, GlassEchoEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(GLASS_ECHO, GlassEchoEntity.createAttributes());
    }
}
