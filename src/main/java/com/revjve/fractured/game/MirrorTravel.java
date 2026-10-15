package com.revjve.fractured.game;

import com.revjve.fractured.block.MirrorBlock;
import com.revjve.fractured.compat.ImmersivePortalsCompat;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crossing between the Main World and the Mirror Dimension through mirror blocks:
 * <ul>
 *   <li><b>Entity Phasing</b> — The Fractured walks or flies into any mirror (no collision) and
 *       steps out of the twin mirror on the other side. The mirror's appearance never changes.
 *       Sneaking suppresses the phase so the killer can lurk beside a mirror.</li>
 *   <li><b>Transformed mirrors</b> — while a mirror is ACTIVE every player can cross. With
 *       Immersive Portals the portal entity handles this seamlessly; otherwise contact teleports.</li>
 * </ul>
 */
public final class MirrorTravel {
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private MirrorTravel() {}

    public static void onMirrorContact(ServerWorld world, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || player.isSpectator()) return;
        if (state.get(MirrorBlock.ACTIVE)) {
            if (ImmersivePortalsCompat.isActive()) return; // the IP portal pair teleports seamlessly
            travel(player, world, pos, state.get(MirrorBlock.FACING));
        } else if (RoleRegistry.isFractured(player) && !player.isSneaking()) {
            travel(player, world, pos, state.get(MirrorBlock.FACING));
        }
    }

    public static boolean travel(ServerPlayerEntity player, ServerWorld from, BlockPos mirrorPos, Direction facing) {
        ServerWorld to = ModDimensions.twin(from);
        if (to == null) return false;
        long now = from.getTime();
        Long readyAt = COOLDOWNS.get(player.getUuid());
        if (readyAt != null && now < readyAt) return false;
        COOLDOWNS.put(player.getUuid(), now + Math.max(10, com.revjve.fractured.FRConfig.get().phaseCooldownTicks));

        BlockPos bottom = mirrorPos;
        while (from.getBlockState(bottom.down()).isOf(ModBlocks.MIRROR)) bottom = bottom.down();
        Vec3d exit = Vec3d.ofBottomCenter(bottom.offset(facing));

        shatterBurst(from, Vec3d.ofCenter(mirrorPos));
        from.playSound(null, mirrorPos, ModSounds.MIRROR_PHASE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.teleport(to, exit.x, exit.y, exit.z, facing.asRotation(), player.getPitch());
        to.playSound(null, exit.x, exit.y, exit.z, ModSounds.MIRROR_PHASE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        shatterBurst(to, exit.add(0, 1, 0));

        MatchManager.get().onMirrorTravel(player, facing);
        return true;
    }

    private static void shatterBurst(ServerWorld world, Vec3d at) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()),
                at.x, at.y, at.z, 24, 0.3, 0.5, 0.3, 0.1);
    }

    public static void clear() {
        COOLDOWNS.clear();
    }
}
