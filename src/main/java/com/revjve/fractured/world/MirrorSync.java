package com.revjve.fractured.world;

import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Instant Main World ⇄ Mirror Dimension block synchronisation. Every successful
 * {@code World#setBlockState} in one arena world (block broken, placed, door opened, candle
 * extinguished, mirror turned into a portal...) is replayed at the same position in the twin
 * world on the same tick, translated through {@link MirrorPalette} so the Mirror Dimension keeps
 * its crimson-and-black materials. Driven by {@code WorldMixin}.
 *
 * <p>Re-entrancy is guarded so the replayed write (and any neighbour cascade it triggers in the
 * twin) is not echoed back. Blocks tagged {@code fractured_reflections:unsynced} are skipped.
 */
public final class MirrorSync {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private MirrorSync() {}

    /** Runs {@code action} with synchronisation suspended (world generation, one-sided objects). */
    public static void runUnsynced(Runnable action) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    public static boolean isSuspended() {
        return DEPTH.get()[0] > 0;
    }

    public static void onBlockChanged(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        if (isSuspended() || !ModDimensions.isArena(world)) return;
        if (state.isIn(ModTags.UNSYNCED)) return;
        ServerWorld twin = ModDimensions.twin(world);
        if (twin == null || twin.isOutOfHeightLimit(pos)) return;
        BlockState translated = ModDimensions.isMirror(world) ? MirrorPalette.toMain(state) : MirrorPalette.toMirror(state);
        BlockState current = twin.getBlockState(pos);
        if (current == translated || current.isIn(ModTags.UNSYNCED)) return;
        runUnsynced(() -> twin.setBlockState(pos, translated, flags));
    }
}
