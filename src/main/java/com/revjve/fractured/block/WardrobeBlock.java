package com.revjve.fractured.block;

import com.revjve.fractured.game.HidingManager;
import com.revjve.fractured.game.RoleRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * A two-block wardrobe survivors can hide in. Use it to climb in, sneak (or use again) to climb
 * out. The Fractured can search it: an empty wardrobe just rattles, an occupied one is torn open
 * and its occupant dragged out stunned. A hidden survivor has no collision with the wardrobe on
 * either logical side ({@link RoleRegistry#isHiding}), so they stand inside it.
 */
public class WardrobeBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

    public WardrobeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        if (pos.getY() >= ctx.getWorld().getTopY() - 1 || !ctx.getWorld().getBlockState(pos.up()).canReplace(ctx)) return null;
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);
        boolean partnerSide = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (partnerSide && !(neighborState.isOf(this) && neighborState.get(HALF) != half)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity != null && RoleRegistry.isHiding(entity)) return VoxelShapes.empty();
        }
        return VoxelShapes.fullCube();
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
            HidingManager.onWardrobeUse(serverPlayer, lower, state.get(FACING));
        }
        return ActionResult.success(world.isClient);
    }
}
