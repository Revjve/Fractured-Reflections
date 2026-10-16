package com.revjve.fractured.block;

import com.revjve.fractured.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Render hook for the escape mirror's vortex and light column. */
public class EscapeMirrorBlockEntity extends BlockEntity {
    public EscapeMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESCAPE_MIRROR, pos, state);
    }
}
