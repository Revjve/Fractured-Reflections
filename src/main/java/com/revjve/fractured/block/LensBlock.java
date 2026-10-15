package com.revjve.fractured.block;

import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A rotatable glass lens for the Refraction Puzzle. Any beam entering the lens leaves through
 * its FACING side. Using it rotates the output clockwise (sneak-use: counter-clockwise).
 */
public class LensBlock extends HorizontalFacingBlock {
    public static final BooleanProperty LIT = Properties.LIT;
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 13, 14);

    public LensBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!world.isClient) {
            Direction facing = state.get(FACING);
            Direction next = player.isSneaking() ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
            world.setBlockState(pos, state.with(FACING, next).with(LIT, false), Block.NOTIFY_ALL);
            world.playSound(null, pos, ModSounds.LENS_ROTATE, SoundCategory.BLOCKS, 0.8f, 0.8f + world.random.nextFloat() * 0.4f);
        }
        return ActionResult.success(world.isClient);
    }
}
