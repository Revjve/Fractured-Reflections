package com.revjve.fractured.world.mansion;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure data description of a generated mansion. Positions are identical in the Main World and
 * the Mirror Dimension.
 */
public final class MansionLayout {
    /** Cell pitch: 11 interior blocks plus one shared wall. */
    public static final int CELL = 12;
    /** Floor pitch: floor slab + 5 blocks of headroom. */
    public static final int FLOOR_H = 6;

    public final BlockPos origin;
    public final int width;
    public final int depth;
    public final int floors;
    public final RoomType[][][] rooms;

    public final List<BlockPos> survivorSpawns = new ArrayList<>();
    public BlockPos fracturedSpawn;
    public final List<AltarSite> altars = new ArrayList<>();
    public final List<PuzzleChestSite> puzzleChests = new ArrayList<>();
    public final List<BlockPos> furnaces = new ArrayList<>();
    public final List<WallSpot> mirrors = new ArrayList<>();
    /** Unused wall-mounted spots, used to spawn Glowing Cracked Mirrors inside the Mirror Dimension. */
    public final List<WallSpot> spareWallSpots = new ArrayList<>();
    public final List<BlockPos> roomCenters = new ArrayList<>();

    public MansionLayout(BlockPos origin, int width, int depth, int floors) {
        this.origin = origin;
        this.width = width;
        this.depth = depth;
        this.floors = floors;
        this.rooms = new RoomType[floors][width][depth];
    }

    public BlockBox bounds() {
        return new BlockBox(origin.getX() - 1, origin.getY() - 1, origin.getZ() - 1,
                origin.getX() + width * CELL + 1, origin.getY() + floors * FLOOR_H + 3, origin.getZ() + depth * CELL + 1);
    }

    /** World position of a cell-local coordinate. */
    public BlockPos at(int i, int j, int floor, int lx, int ly, int lz) {
        return origin.add(i * CELL + lx, floor * FLOOR_H + ly, j * CELL + lz);
    }

    public boolean contains(BlockPos pos) {
        return bounds().contains(pos);
    }

    /** A wall-mounted position: {@code pos} is the block hugging the wall, {@code facing} points into the room. */
    public record WallSpot(BlockPos pos, Direction facing) {}

    public record AltarSite(int index, BlockPos altar, List<BlockPos> emitters, List<BlockPos> focals, List<BlockPos> lenses) {}

    public record PuzzleChestSite(BlockPos pos, int[] code) {}
}
