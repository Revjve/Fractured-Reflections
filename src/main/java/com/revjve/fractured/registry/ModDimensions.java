package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The two arena dimensions are declared as datapack JSON
 * ({@code data/fractured_reflections/dimension/*.json}). Both are void worlds; the mansion is
 * generated into each at identical coordinates so positions map 1:1 between them.
 */
public final class ModDimensions {
    public static final Identifier MANOR_ID = FracturedReflections.id("manor");
    public static final Identifier MIRROR_ID = FracturedReflections.id("mirror_realm");

    public static final RegistryKey<World> MANOR = RegistryKey.of(RegistryKeys.WORLD, MANOR_ID);
    public static final RegistryKey<World> MIRROR = RegistryKey.of(RegistryKeys.WORLD, MIRROR_ID);

    private ModDimensions() {}

    public static boolean isArena(World world) {
        return isArena(world.getRegistryKey());
    }

    public static boolean isArena(RegistryKey<World> key) {
        return key == MANOR || key == MIRROR;
    }

    public static boolean isMirror(World world) {
        return world.getRegistryKey() == MIRROR;
    }

    /** The other half of the Main World / Mirror Dimension pair, or null if {@code world} is not an arena world. */
    @Nullable
    public static ServerWorld twin(ServerWorld world) {
        MinecraftServer server = world.getServer();
        RegistryKey<World> key = world.getRegistryKey();
        if (key == MANOR) return server.getWorld(MIRROR);
        if (key == MIRROR) return server.getWorld(MANOR);
        return null;
    }
}
