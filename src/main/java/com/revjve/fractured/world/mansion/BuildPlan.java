package com.revjve.fractured.world.mansion;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ordered block placements for both worlds. Phases are applied in order so attachments
 * (lanterns, torches, candles) always find their support already in place.
 */
public final class BuildPlan {
    public enum Target { BOTH, MAIN, MIRROR }

    public enum Phase {
        /** Floors, walls, roof: no neighbour updates (fast). */
        STRUCTURE(Block.NOTIFY_LISTENERS | Block.FORCE_STATE),
        /** Furniture: neighbour updates on so fences/walls/panes connect. */
        DECOR(Block.NOTIFY_ALL),
        /** Things that hang from or sit on other blocks. */
        ATTACHMENTS(Block.NOTIFY_ALL),
        /** Two-block structures placed half by half without validation. */
        DOORS(Block.NOTIFY_LISTENERS | Block.FORCE_STATE);

        public final int flags;

        Phase(int flags) {
            this.flags = flags;
        }
    }

    public record Placement(BlockPos pos, BlockState state, Target target) {}

    public record Finalizer(Target target, Consumer<ServerWorld> action) {}

    @SuppressWarnings("unchecked")
    private final List<Placement>[] phases = new List[Phase.values().length];
    private final List<Finalizer> finalizers = new ArrayList<>();

    public BuildPlan() {
        for (int i = 0; i < phases.length; i++) phases[i] = new ArrayList<>();
    }

    public void add(Phase phase, BlockPos pos, BlockState state, Target target) {
        phases[phase.ordinal()].add(new Placement(pos.toImmutable(), state, target));
    }

    public void add(Phase phase, BlockPos pos, BlockState state) {
        add(phase, pos, state, Target.BOTH);
    }

    public List<Placement> phase(Phase phase) {
        return phases[phase.ordinal()];
    }

    public void addFinalizer(Target target, Consumer<ServerWorld> action) {
        finalizers.add(new Finalizer(target, action));
    }

    public List<Finalizer> finalizers() {
        return finalizers;
    }

    public int size() {
        int total = 0;
        for (List<Placement> list : phases) total += list.size();
        return total;
    }
}
