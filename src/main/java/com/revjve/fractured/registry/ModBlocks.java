package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.block.BeamEmitterBlock;
import com.revjve.fractured.block.EscapeMirrorBlock;
import com.revjve.fractured.block.FocalPointBlock;
import com.revjve.fractured.block.LensBlock;
import com.revjve.fractured.block.MirrorBlock;
import com.revjve.fractured.block.PuzzleChestBlock;
import com.revjve.fractured.block.RitualAltarBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    /** Arena furniture is unbreakable outside creative; survivors interact, they don't dismantle. */
    private static AbstractBlock.Settings arena(MapColor color, BlockSoundGroup sounds) {
        return AbstractBlock.Settings.create()
                .mapColor(color)
                .strength(-1.0f, 3600000.0f)
                .dropsNothing()
                .sounds(sounds)
                .pistonBehavior(PistonBehavior.BLOCK);
    }

    public static final Block MIRROR = register("mirror", new MirrorBlock(arena(MapColor.LIGHT_BLUE, BlockSoundGroup.GLASS)
            .nonOpaque()
            .luminance(state -> state.get(MirrorBlock.ACTIVE) ? 11 : 0)));

    public static final Block ESCAPE_MIRROR = register("glowing_cracked_mirror", new EscapeMirrorBlock(arena(MapColor.BLUE, BlockSoundGroup.GLASS)
            .nonOpaque()
            .noCollision()
            .luminance(state -> 13)));

    public static final Block RITUAL_ALTAR = register("ritual_altar", new RitualAltarBlock(arena(MapColor.PURPLE, BlockSoundGroup.AMETHYST_BLOCK)
            .nonOpaque()
            .luminance(state -> 3 + state.get(RitualAltarBlock.STAGE) * 4)));

    public static final Block REFRACTION_LENS = register("refraction_lens", new LensBlock(arena(MapColor.CYAN, BlockSoundGroup.GLASS)
            .nonOpaque()
            .luminance(state -> state.get(LensBlock.LIT) ? 9 : 0)));

    public static final Block LUMEN_PRISM = register("lumen_prism", new BeamEmitterBlock(arena(MapColor.BLACK, BlockSoundGroup.POLISHED_DEEPSLATE)
            .luminance(state -> 10)));

    public static final Block FOCAL_POINT = register("focal_point", new FocalPointBlock(arena(MapColor.WHITE, BlockSoundGroup.AMETHYST_BLOCK)
            .luminance(state -> state.get(FocalPointBlock.LIT) ? 14 : 2)));

    public static final Block PUZZLE_CHEST = register("puzzle_chest", new PuzzleChestBlock(arena(MapColor.DARK_GREEN, BlockSoundGroup.WOOD)
            .nonOpaque()));

    private ModBlocks() {}

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, FracturedReflections.id(name), block);
    }

    public static void register() {
        // class-load trigger
    }
}
