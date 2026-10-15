package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.registry.ModSounds;
import com.revjve.fractured.registry.ModTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shatter Blackout: every tagged light source within the radius is shattered (lanterns, torches,
 * end rods...) or extinguished (candles, campfires). Changes propagate to the twin world through
 * MirrorSync. If configured, the glass "reforms" after a delay and lights come back.
 */
public final class BlackoutManager {
    private record Shattered(ServerWorld world, BlockPos pos, BlockState original, BlockState replaced, long restoreAt) {}

    private final List<Shattered> shattered = new ArrayList<>();

    /** @return number of lights affected. */
    public int trigger(ServerWorld world, BlockPos center, int radius) {
        int restoreSeconds = FRConfig.get().blackoutRestoreSeconds;
        long restoreAt = restoreSeconds > 0 ? world.getTime() + restoreSeconds * 20L : Long.MAX_VALUE;
        int radiusSq = radius * radius;
        int vertical = Math.min(radius, 14);
        int count = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                for (int dy = -vertical; dy <= vertical; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(p);
                    if (!state.isIn(ModTags.SHATTERABLE_LIGHTS)) continue;
                    BlockState replaced;
                    if (state.contains(Properties.LIT)) {
                        if (!state.get(Properties.LIT)) continue;
                        replaced = state.with(Properties.LIT, false);
                    } else {
                        replaced = Blocks.AIR.getDefaultState();
                        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, p, Block.getRawIdFromState(state));
                    }
                    BlockPos immutable = p.toImmutable();
                    world.setBlockState(immutable, replaced, Block.NOTIFY_ALL);
                    shattered.add(new Shattered(world, immutable, state, replaced, restoreAt));
                    count++;
                }
            }
        }
        world.playSound(null, center, ModSounds.BLACKOUT, SoundCategory.HOSTILE, 4.0f, 0.8f);
        return count;
    }

    public void tick() {
        Iterator<Shattered> it = shattered.iterator();
        ServerWorld soundWorld = null;
        BlockPos soundPos = null;
        while (it.hasNext()) {
            Shattered s = it.next();
            if (s.world().getTime() < s.restoreAt()) continue;
            if (s.world().getBlockState(s.pos()) == s.replaced()) {
                s.world().setBlockState(s.pos(), s.original(), Block.NOTIFY_ALL);
                soundWorld = s.world();
                soundPos = s.pos();
            }
            it.remove();
        }
        if (soundWorld != null) soundWorld.playSound(null, soundPos, ModSounds.LIGHTS_REFORM, SoundCategory.BLOCKS, 2.0f, 1.0f);
    }

    public void clear() {
        shattered.clear();
    }
}
