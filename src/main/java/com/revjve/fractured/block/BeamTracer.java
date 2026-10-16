package com.revjve.fractured.block;

import com.revjve.fractured.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refraction-puzzle light transport, shared by the server (which decides whether the altar's
 * focal points are lit) and the client (which renders the laser beams from the same trace).
 * A beam leaves a Lumen Prism through its face, passes through air and transparent blocks, is
 * redirected by every lens to that lens's FACING, and stops at a focal point or an opaque block.
 */
public final class BeamTracer {
    public static final int MAX_STEPS = 40;

    /** One straight beam segment between block centres. */
    public record Segment(BlockPos from, BlockPos to, boolean terminated) {}

    public record Result(List<Segment> segments, Set<BlockPos> litLenses, Set<BlockPos> litFocals) {}

    private BeamTracer() {}

    public static Result trace(BlockView world, List<BlockPos> emitters) {
        List<Segment> segments = new ArrayList<>();
        Set<BlockPos> litLenses = new HashSet<>();
        Set<BlockPos> litFocals = new HashSet<>();
        for (BlockPos emitterPos : emitters) {
            BlockState emitter = world.getBlockState(emitterPos);
            if (!emitter.isOf(ModBlocks.LUMEN_PRISM)) continue;
            Direction dir = emitter.get(BeamEmitterBlock.FACING);
            BlockPos start = emitterPos;
            BlockPos cursor = emitterPos.offset(dir);
            boolean done = false;
            for (int step = 0; step < MAX_STEPS && !done; step++) {
                BlockState st = world.getBlockState(cursor);
                if (st.isOf(ModBlocks.REFRACTION_LENS)) {
                    BlockPos lens = cursor.toImmutable();
                    if (!litLenses.add(lens)) {
                        done = true; // beam loop
                    } else {
                        segments.add(new Segment(start, lens, false));
                        start = lens;
                        dir = st.get(LensBlock.FACING);
                    }
                } else if (st.isOf(ModBlocks.FOCAL_POINT)) {
                    litFocals.add(cursor.toImmutable());
                    segments.add(new Segment(start, cursor.toImmutable(), true));
                    done = true;
                } else if (!st.isAir() && st.isOpaqueFullCube(world, cursor)) {
                    segments.add(new Segment(start, cursor.toImmutable(), true));
                    done = true;
                }
                cursor = cursor.offset(dir);
            }
            if (!done) segments.add(new Segment(start, cursor.toImmutable(), true));
        }
        return new Result(segments, litLenses, litFocals);
    }
}
