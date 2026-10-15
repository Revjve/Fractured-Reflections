package com.revjve.fractured.block;

import com.revjve.fractured.game.EscapeMirrors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Glowing Blue Cracked Mirror: spawned procedurally inside the Mirror Dimension whenever a
 * survivor is trapped there. Jumping through it returns the survivor to the Main World.
 */
public class EscapeMirrorBlock extends HorizontalFacingBlock {
    public EscapeMirrorBlock(Settings settings) {
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
        Direction side = ctx.getSide();
        return getDefaultState().with(FACING, side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return WallPlateShapes.forFacing(state.get(FACING));
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world instanceof ServerWorld serverWorld && entity instanceof ServerPlayerEntity player) {
            EscapeMirrors.onContact(serverWorld, pos, state, player);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Direction facing = state.get(FACING);
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 - facing.getOffsetX() * 0.3 + (random.nextDouble() - 0.5) * 0.8;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5 - facing.getOffsetZ() * 0.3 + (random.nextDouble() - 0.5) * 0.8;
            world.addParticle(random.nextBoolean() ? ParticleTypes.GLOW : ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, facing.getOffsetX() * 0.02, 0.01, facing.getOffsetZ() * 0.02);
        }
    }
}
