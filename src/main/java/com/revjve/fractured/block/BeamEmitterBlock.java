package com.revjve.fractured.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** The Lumen Prism: a fixed light source that casts a beam out of its FACING side. */
public class BeamEmitterBlock extends HorizontalFacingBlock {
    public BeamEmitterBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Direction facing = state.get(FACING);
        world.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5 + facing.getOffsetX() * 0.55,
                pos.getY() + 0.5,
                pos.getZ() + 0.5 + facing.getOffsetZ() * 0.55,
                facing.getOffsetX() * 0.03, 0, facing.getOffsetZ() * 0.03);
    }
}
