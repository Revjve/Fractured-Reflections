package com.revjve.fractured.world.mansion;

import com.revjve.fractured.world.MirrorSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Applies a {@link BuildPlan} to the Main World and the Mirror Dimension over several ticks:
 * clear the arena volume, place every phase, then run block-entity finalizers. All writes run
 * with mirror sync suppressed, since both worlds are written explicitly.
 */
public final class BuildJob {
    private enum Stage { CLEAR, PLACE, FINALIZE, DONE }

    private final ServerWorld main;
    private final ServerWorld mirror;
    private final BuildPlan plan;
    private final BlockBox box;
    private Stage stage = Stage.CLEAR;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private int cx, cy, cz;
    private int phase;
    private int index;
    private final int total;
    private int visited;
    private int placed;

    public BuildJob(ServerWorld main, ServerWorld mirror, MansionLayout layout, BuildPlan plan) {
        this.main = main;
        this.mirror = mirror;
        this.plan = plan;
        this.box = layout.bounds();
        this.cx = box.getMinX();
        this.cy = box.getMaxY();
        this.cz = box.getMinZ();
        this.total = box.getBlockCountX() * box.getBlockCountY() * box.getBlockCountZ() / 4 + plan.size();
    }

    public void begin() {
        for (ServerWorld world : new ServerWorld[]{main, mirror}) {
            setChunksForced(world, box, true);
            Box area = Box.from(box).expand(2);
            List<Entity> leftovers = world.getEntitiesByClass(Entity.class, area, e -> !(e instanceof PlayerEntity));
            leftovers.forEach(Entity::discard);
        }
    }

    public static void setChunksForced(ServerWorld world, BlockBox box, boolean forced) {
        for (int x = box.getMinX() >> 4; x <= box.getMaxX() >> 4; x++) {
            for (int z = box.getMinZ() >> 4; z <= box.getMaxZ() >> 4; z++) {
                world.setChunkForced(x, z, forced);
            }
        }
    }

    public float progress() {
        return total == 0 ? 1f : Math.min(1f, (visited / 4 + placed) / (float) total);
    }

    public boolean isDone() {
        return stage == Stage.DONE;
    }

    /** Performs up to {@code budget} block writes per world. */
    public void tick(int budget) {
        MirrorSync.runUnsynced(() -> {
            int remaining = budget;
            while (remaining > 0 && stage != Stage.DONE) {
                switch (stage) {
                    case CLEAR -> remaining = clear(remaining);
                    case PLACE -> remaining = place(remaining);
                    case FINALIZE -> {
                        for (BuildPlan.Finalizer f : plan.finalizers()) {
                            if (f.target() != BuildPlan.Target.MIRROR) f.action().accept(main);
                            if (f.target() != BuildPlan.Target.MAIN) f.action().accept(mirror);
                        }
                        stage = Stage.DONE;
                    }
                    default -> remaining = 0;
                }
            }
        });
    }

    private int clear(int budget) {
        BlockState air = Blocks.AIR.getDefaultState();
        int reads = budget * 6;
        while (budget > 0 && reads-- > 0) {
            cursor.set(cx, cy, cz);
            for (ServerWorld world : new ServerWorld[]{main, mirror}) {
                if (!world.getBlockState(cursor).isAir()) {
                    world.setBlockState(cursor, air, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                    budget--;
                }
            }
            visited++;
            if (++cx > box.getMaxX()) {
                cx = box.getMinX();
                if (++cz > box.getMaxZ()) {
                    cz = box.getMinZ();
                    if (--cy < box.getMinY()) {
                        stage = Stage.PLACE;
                        return budget;
                    }
                }
            }
        }
        return 0;
    }

    private int place(int budget) {
        BuildPlan.Phase[] phases = BuildPlan.Phase.values();
        while (budget > 0) {
            if (phase >= phases.length) {
                stage = Stage.FINALIZE;
                return budget;
            }
            List<BuildPlan.Placement> list = plan.phase(phases[phase]);
            if (index >= list.size()) {
                phase++;
                index = 0;
                continue;
            }
            BuildPlan.Placement p = list.get(index++);
            int flags = phases[phase].flags;
            if (p.target() != BuildPlan.Target.MIRROR) main.setBlockState(p.pos(), p.state(), flags);
            if (p.target() != BuildPlan.Target.MAIN) mirror.setBlockState(p.pos(), p.state(), flags);
            budget--;
            placed++;
        }
        return 0;
    }

    /** Called when a match ends to release the force-loaded chunks. */
    public static void release(ServerWorld main, ServerWorld mirror, MansionLayout layout) {
        setChunksForced(main, layout.bounds(), false);
        setChunksForced(mirror, layout.bounds(), false);
    }
}
