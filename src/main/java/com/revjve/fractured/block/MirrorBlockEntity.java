package com.revjve.fractured.block;

import com.revjve.fractured.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Exists so the mirror surface can be drawn by a BlockEntityRenderer (planar sheen when idle,
 * the vanilla end-portal shader when transformed). Portal timers live in PortalManager.
 */
public class MirrorBlockEntity extends BlockEntity {
    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MIRROR, pos, state);
    }
}
