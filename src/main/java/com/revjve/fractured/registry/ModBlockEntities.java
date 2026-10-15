package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.block.MirrorBlockEntity;
import com.revjve.fractured.block.PuzzleChestBlockEntity;
import com.revjve.fractured.block.RitualAltarBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<MirrorBlockEntity> MIRROR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            FracturedReflections.id("mirror"),
            FabricBlockEntityTypeBuilder.create(MirrorBlockEntity::new, ModBlocks.MIRROR).build());

    public static final BlockEntityType<RitualAltarBlockEntity> RITUAL_ALTAR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            FracturedReflections.id("ritual_altar"),
            FabricBlockEntityTypeBuilder.create(RitualAltarBlockEntity::new, ModBlocks.RITUAL_ALTAR).build());

    public static final BlockEntityType<PuzzleChestBlockEntity> PUZZLE_CHEST = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            FracturedReflections.id("puzzle_chest"),
            FabricBlockEntityTypeBuilder.create(PuzzleChestBlockEntity::new, ModBlocks.PUZZLE_CHEST).build());

    private ModBlockEntities() {}

    public static void register() {
        // class-load trigger
    }
}
