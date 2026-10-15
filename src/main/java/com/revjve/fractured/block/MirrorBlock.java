package com.revjve.fractured.block;

import com.revjve.fractured.game.MirrorTravel;
import com.revjve.fractured.game.RoleRegistry;
import com.revjve.fractured.registry.ModParticles;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A wall mirror present at the same position in both the Main World and the Mirror Dimension.
 * <ul>
 *   <li>Idle: renders a glassy, sheened surface (see MirrorBlockEntityRenderer) and is solid —
 *       it does not show the other dimension.</li>
 *   <li>The Fractured has no collision with it and phases into the twin world on contact.</li>
 *   <li>ACTIVE (portal transformation): non-solid for everyone and linked to the twin mirror.
 *       ACTIVE is a block-state property, so MirrorSync opens the twin automatically.</li>
 * </ul>
 */
public class MirrorBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public MirrorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(ACTIVE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return WallPlateShapes.forFacing(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(ACTIVE)) return VoxelShapes.empty();
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity != null && RoleRegistry.isFractured(entity)) return VoxelShapes.empty();
        }
        return WallPlateShapes.forFacing(state.get(FACING));
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world instanceof ServerWorld serverWorld) {
            MirrorTravel.onMirrorContact(serverWorld, pos, state, entity);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Direction facing = state.get(FACING);
        double x = pos.getX() + 0.5 - facing.getOffsetX() * 0.35 + (facing.getAxis() == Direction.Axis.Z ? random.nextDouble() - 0.5 : 0);
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + 0.5 - facing.getOffsetZ() * 0.35 + (facing.getAxis() == Direction.Axis.X ? random.nextDouble() - 0.5 : 0);
        if (state.get(ACTIVE)) {
            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, facing.getOffsetX() * 0.05, 0.02, facing.getOffsetZ() * 0.05);
            }
        } else if (random.nextInt(6) == 0) {
            world.addParticle(ModParticles.GLASS_MOTE, x, y, z, facing.getOffsetX() * 0.01, 0.005, facing.getOffsetZ() * 0.01);
        }
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MirrorBlockEntity(pos, state);
    }
}
