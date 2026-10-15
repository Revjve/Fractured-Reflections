package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class ModTags {
    /** Light sources that Shatter Blackout breaks (or extinguishes, if they have a LIT property). */
    public static final TagKey<Block> SHATTERABLE_LIGHTS = TagKey.of(RegistryKeys.BLOCK, FracturedReflections.id("shatterable_lights"));
    /** Blocks whose changes are never mirrored into the twin world (puzzle pieces, escape mirrors...). */
    public static final TagKey<Block> UNSYNCED = TagKey.of(RegistryKeys.BLOCK, FracturedReflections.id("unsynced"));

    private ModTags() {}
}
