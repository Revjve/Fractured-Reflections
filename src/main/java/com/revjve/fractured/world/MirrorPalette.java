package com.revjve.fractured.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Property;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Mirror Dimension is the same mansion rebuilt in blood and soot: dark oak becomes crimson,
 * deepslate becomes nether brick, glass turns red or black, moss becomes crimson nylium, torches
 * burn redstone-red and skulls turn to wither skulls.
 *
 * <p>The mapping is a bijection over blocks the generator actually uses, so {@link MirrorSync}
 * can translate every change in either direction ({@link #toMirror}/{@link #toMain}) while
 * copying shared properties (facing, half, open, lit, axis, rotation...). Mirror-side targets
 * never appear in the Main World generator, so the reverse lookup is unambiguous.
 */
public final class MirrorPalette {
    private static final Map<Block, Block> TO_MIRROR = new IdentityHashMap<>();
    private static final Map<Block, Block> TO_MAIN = new IdentityHashMap<>();

    static {
        // structure
        pair(Blocks.DARK_OAK_PLANKS, Blocks.CRIMSON_PLANKS);
        pair(Blocks.SPRUCE_PLANKS, Blocks.MANGROVE_PLANKS);
        pair(Blocks.DARK_OAK_LOG, Blocks.CRIMSON_STEM);
        pair(Blocks.DEEPSLATE_BRICKS, Blocks.NETHER_BRICKS);
        pair(Blocks.DEEPSLATE_TILES, Blocks.BLACKSTONE);
        pair(Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        pair(Blocks.POLISHED_DEEPSLATE, Blocks.RED_NETHER_BRICKS);
        pair(Blocks.SMOOTH_QUARTZ, Blocks.RED_CONCRETE);
        pair(Blocks.DEEPSLATE_TILE_WALL, Blocks.NETHER_BRICK_WALL);
        pair(Blocks.TINTED_GLASS, Blocks.RED_STAINED_GLASS);
        pair(Blocks.GLASS, Blocks.BLACK_STAINED_GLASS);
        pair(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.RED_GLAZED_TERRACOTTA);
        // wood furniture
        pair(Blocks.DARK_OAK_STAIRS, Blocks.CRIMSON_STAIRS);
        pair(Blocks.DARK_OAK_SLAB, Blocks.CRIMSON_SLAB);
        pair(Blocks.DARK_OAK_FENCE, Blocks.CRIMSON_FENCE);
        pair(Blocks.DARK_OAK_DOOR, Blocks.CRIMSON_DOOR);
        pair(Blocks.DARK_OAK_WALL_SIGN, Blocks.CRIMSON_WALL_SIGN);
        pair(Blocks.DARK_OAK_TRAPDOOR, Blocks.CRIMSON_TRAPDOOR);
        pair(Blocks.WHITE_WOOL, Blocks.RED_WOOL);
        // conservatory
        pair(Blocks.MOSS_BLOCK, Blocks.CRIMSON_NYLIUM);
        pair(Blocks.MOSSY_STONE_BRICKS, Blocks.NETHER_WART_BLOCK);
        pair(Blocks.MOSSY_COBBLESTONE, Blocks.GILDED_BLACKSTONE);
        pair(Blocks.AZALEA, Blocks.CRIMSON_FUNGUS);
        pair(Blocks.FLOWERING_AZALEA, Blocks.CRIMSON_ROOTS);
        pair(Blocks.POTTED_FERN, Blocks.POTTED_CRIMSON_ROOTS);
        pair(Blocks.POTTED_AZALEA_BUSH, Blocks.POTTED_CRIMSON_FUNGUS);
        pair(Blocks.POTTED_DEAD_BUSH, Blocks.POTTED_RED_MUSHROOM);
        pair(Blocks.MOSS_CARPET, Blocks.BLACK_CARPET);
        // trim & lights
        pair(Blocks.RED_CARPET, Blocks.BROWN_CARPET);
        pair(Blocks.CANDLE, Blocks.RED_CANDLE);
        pair(Blocks.WHITE_CANDLE, Blocks.BLACK_CANDLE);
        pair(Blocks.GRAY_CANDLE, Blocks.BROWN_CANDLE);
        pair(Blocks.YELLOW_CANDLE, Blocks.ORANGE_CANDLE);
        pair(Blocks.WALL_TORCH, Blocks.REDSTONE_WALL_TORCH);
        pair(Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL);
    }

    private MirrorPalette() {}

    private static void pair(Block main, Block mirror) {
        if (TO_MAIN.containsKey(main) || TO_MIRROR.containsKey(mirror)) {
            throw new IllegalStateException("Mirror palette is not a bijection at " + main + " / " + mirror);
        }
        TO_MIRROR.put(main, mirror);
        TO_MAIN.put(mirror, main);
    }

    public static BlockState toMirror(BlockState state) {
        return convert(state, TO_MIRROR);
    }

    public static BlockState toMain(BlockState state) {
        return convert(state, TO_MAIN);
    }

    private static BlockState convert(BlockState state, Map<Block, Block> map) {
        Block target = map.get(state.getBlock());
        if (target == null) return state;
        BlockState out = target.getDefaultState();
        for (Property<?> property : state.getProperties()) {
            if (out.contains(property)) out = copy(state, out, property);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.with(property, from.get(property));
    }
}
