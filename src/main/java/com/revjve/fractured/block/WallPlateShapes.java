package com.revjve.fractured.block;

import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Shapes for wall-mounted, 2px-deep plates. FACING is the direction the reflective face points
 * (into the room), so the plate hugs the opposite side of the block cell.
 */
final class WallPlateShapes {
    static final VoxelShape NORTH = Block.createCuboidShape(0, 0, 14, 16, 16, 16);
    static final VoxelShape SOUTH = Block.createCuboidShape(0, 0, 0, 16, 16, 2);
    static final VoxelShape EAST = Block.createCuboidShape(0, 0, 0, 2, 16, 16);
    static final VoxelShape WEST = Block.createCuboidShape(14, 0, 0, 16, 16, 16);

    private WallPlateShapes() {}

    static VoxelShape forFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }
}
