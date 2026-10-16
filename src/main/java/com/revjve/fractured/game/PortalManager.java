package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.block.MirrorBlock;
import com.revjve.fractured.compat.ImmersivePortalsCompat;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.network.ServerNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Portal Transformation: The Fractured turns a mirror column into an open dimensional portal
 * for everyone, for a limited time. ACTIVE is a block-state property, so the twin mirror opens
 * through MirrorSync; with Immersive Portals a seamless two-way portal pair is spawned too.
 */
public final class PortalManager {
    private record OpenPortal(ServerWorld world, BlockPos bottom, int height, long closesAt, List<Entity> entities) {}

    private final List<OpenPortal> open = new ArrayList<>();
    private final List<Entity> reflections = new ArrayList<>();

    /** @return false if {@code pos} is not an idle mirror. */
    public boolean open(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(ModBlocks.MIRROR) || state.get(MirrorBlock.ACTIVE)) return false;
        Direction facing = state.get(MirrorBlock.FACING);

        BlockPos bottom = pos;
        while (isMirror(world, bottom.down(), facing)) bottom = bottom.down();
        int height = 1;
        while (isMirror(world, bottom.up(height), facing) && height < 4) height++;

        for (int n = 0; n < height; n++) {
            BlockPos p = bottom.up(n);
            world.setBlockState(p, world.getBlockState(p).with(MirrorBlock.ACTIVE, true), Block.NOTIFY_ALL);
        }

        List<Entity> entities = new ArrayList<>();
        ServerWorld twin = ModDimensions.twin(world);
        if (twin != null && ImmersivePortalsCompat.isActive()) {
            Vec3d center = Vec3d.ofBottomCenter(bottom).add(0, height / 2.0, 0)
                    .subtract(Vec3d.of(facing.getVector()).multiply(0.15));
            entities.addAll(ImmersivePortalsCompat.openMirrorPortal(world, twin, center, facing, 0.9, height - 0.1));
        }

        long closesAt = world.getTime() + FRConfig.get().portalDurationSeconds * 20L;
        open.add(new OpenPortal(world, bottom, height, closesAt, entities));
        world.playSound(null, bottom, ModSounds.PORTAL_OPEN, SoundCategory.BLOCKS, 2.5f, 1.0f);
        if (twin != null) twin.playSound(null, bottom, ModSounds.PORTAL_OPEN, SoundCategory.BLOCKS, 2.5f, 0.8f);
        Vec3d face = Vec3d.ofBottomCenter(bottom).add(0, height / 2.0, 0);
        for (ServerWorld w : twin == null ? new ServerWorld[]{world} : new ServerWorld[]{world, twin}) {
            shockwave(w, face, facing);
            for (ServerPlayerEntity p : w.getPlayers()) {
                double d = p.getPos().distanceTo(face);
                if (d < 20) ServerNetworking.sendScreenEffect(p, FRPackets.ScreenEffect.PORTAL, (float) (1.0 - d / 20.0));
            }
        }
        return true;
    }

    /** An expanding ring of glass and rift sparks blown out of the mirror face. */
    private static void shockwave(ServerWorld world, Vec3d center, Direction facing) {
        Direction side = facing.rotateYClockwise();
        for (int n = 0; n < 40; n++) {
            double a = n * Math.PI * 2 / 40;
            double dx = side.getOffsetX() * Math.cos(a), dz = side.getOffsetZ() * Math.cos(a), dy = Math.sin(a);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 0, dx, dy, dz, 0.35);
            world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 0,
                    dx + facing.getOffsetX() * 0.6, dy, dz + facing.getOffsetZ() * 0.6, 0.18);
        }
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()),
                center.x + facing.getOffsetX() * 0.3, center.y, center.z + facing.getOffsetZ() * 0.3, 40, 0.3, 0.6, 0.3, 0.2);
    }

    private static boolean isMirror(ServerWorld world, BlockPos pos, Direction facing) {
        BlockState s = world.getBlockState(pos);
        return s.isOf(ModBlocks.MIRROR) && s.get(MirrorBlock.FACING) == facing;
    }

    public void tick() {
        Iterator<OpenPortal> it = open.iterator();
        while (it.hasNext()) {
            OpenPortal portal = it.next();
            if (portal.world().getTime() >= portal.closesAt()) {
                close(portal);
                it.remove();
            }
        }
    }

    private static void close(OpenPortal portal) {
        ServerWorld world = portal.world();
        for (int n = 0; n < portal.height(); n++) {
            BlockPos p = portal.bottom().up(n);
            BlockState s = world.getBlockState(p);
            if (s.isOf(ModBlocks.MIRROR) && s.get(MirrorBlock.ACTIVE)) {
                world.setBlockState(p, s.with(MirrorBlock.ACTIVE, false), Block.NOTIFY_ALL);
            }
        }
        portal.entities().forEach(Entity::discard);
        world.playSound(null, portal.bottom(), ModSounds.PORTAL_CLOSE, SoundCategory.BLOCKS, 2.0f, 1.0f);
    }

    public void closeAll() {
        open.forEach(PortalManager::close);
        open.clear();
        reflections.forEach(Entity::discard);
        reflections.clear();
    }

    /** Optional IP Mirror entities over every mirror column for true planar reflections. */
    public void spawnReflections(ServerWorld world, List<com.revjve.fractured.world.mansion.MansionLayout.WallSpot> mirrors) {
        if (!FRConfig.get().immersivePortalsReflections || !ImmersivePortalsCompat.isActive()) return;
        for (var spot : mirrors) {
            if (world.getBlockState(spot.pos().down()).isOf(ModBlocks.MIRROR)) continue; // only column bottoms
            Vec3d center = Vec3d.ofBottomCenter(spot.pos()).add(0, 1.0, 0)
                    .subtract(Vec3d.of(spot.facing().getVector()).multiply(0.37));
            Entity reflection = ImmersivePortalsCompat.spawnReflection(world, center, spot.facing(), 0.85, 1.85);
            if (reflection != null) reflections.add(reflection);
        }
    }
}
